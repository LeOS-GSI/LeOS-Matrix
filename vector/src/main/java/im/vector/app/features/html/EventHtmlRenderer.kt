/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.html

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.text.Spannable
import androidx.core.text.toSpannable
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.target.Target
import im.vector.app.R
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.resources.ColorProvider
import im.vector.app.core.utils.DimensionConverter
import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.themes.ThemeUtils
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.PrecomputedFutureTextSetterCompat
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.latex.JLatexMathTheme
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import org.commonmark.node.Node
import org.matrix.android.sdk.api.MatrixUrls.isMxcUrl
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventHtmlRenderer @Inject constructor(
        private val htmlConfigure: MatrixHtmlPluginConfigure,
        private val context: Context,
        private val activeSessionHolder: ActiveSessionHolder,
        private val vectorPreferences: VectorPreferences
) {

    private fun resolveCodeBlockBackground() =
            ThemeUtils.getColor(context, R.attr.code_block_bg_color)
    private fun resolveQuoteBarColor() =
            ThemeUtils.getColor(context, R.attr.quote_bar_color)

    private var codeBlockBackground: Int = resolveCodeBlockBackground()
    private var quoteBarColor: Int = resolveQuoteBarColor()

    interface PostProcessor {
        fun afterRender(renderedText: Spannable)
    }

    private fun String.removeHeightWidthAttrs(): String {
        return replace(Regex("""height="([^"]*)""""), "")
                .replace(Regex("""width="([^"]*)""""), "")
    }

    private fun buildMarkwon() = Markwon.builder(context)
            .usePlugins(listOf(
                    HtmlPlugin.create(htmlConfigure),
                    object: AbstractMarkwonPlugin() {
                        override fun configureTheme(builder: MarkwonTheme.Builder) {
                            super.configureTheme(builder)
                            builder.codeBlockBackgroundColor(codeBlockBackground)
                                    .codeBackgroundColor(codeBlockBackground)
                                    .blockQuoteColor(quoteBarColor)
                        }
                    },
                    object : AbstractMarkwonPlugin() { // Overwrite height for data-mx-emoticon, to ensure emoji-like height
                        override fun processMarkdown(markdown: String): String {
                            return markdown
                                    .replace(Regex("""<img\s+([^>]*)data-mx-emoticon([^>]*)>""")) { matchResult ->
                                        """<img height="1.2em" """ + matchResult.groupValues[1].removeHeightWidthAttrs() +
                                                " data-mx-emoticon" + matchResult.groupValues[2].removeHeightWidthAttrs() + ">"
                                    }
                        }
                    },
                    GlideImagesPlugin.create(object: GlideImagesPlugin.GlideStore {
                        override fun load(drawable: AsyncDrawable): RequestBuilder<Drawable> {
                            val url = drawable.destination
                            if (url.isMxcUrl()) {
                                val contentUrlResolver = activeSessionHolder.getActiveSession().contentUrlResolver()
                                val imageUrl = contentUrlResolver.resolveFullSize(url)
                                // Override size to avoid crashes for huge pictures
                                return Glide.with(context).load(imageUrl).override(500)
                            }
                            // We don't want to support other url schemes here, so just return a request for null
                            return Glide.with(context).load(null as String?)
                        }

                        override fun cancel(target: Target<*>) {
                            Glide.with(context).clear(target)
                        }
                    })
            ))
            .apply {
                if (vectorPreferences.latexMathsIsEnabled()) {
                        usePlugin(object : AbstractMarkwonPlugin() { // Markwon expects maths to be in a specific format: https://noties.io/Markwon/docs/v4/ext-latex
                            override fun processMarkdown(markdown: String): String {
                                return markdown
                                        .replace(Regex("""<span\s+data-mx-maths="([^"]*)">.*?</span>""")) { matchResult ->
                                            "$$" + matchResult.groupValues[1] + "$$"
                                        }
                                        .replace(Regex("""<div\s+data-mx-maths="([^"]*)">.*?</div>""")) { matchResult ->
                                            "\n$$\n" + matchResult.groupValues[1] + "\n$$\n"
                                        }
                            }
                        })
                        .usePlugin(MarkwonInlineParserPlugin.create())
                        .usePlugin(JLatexMathPlugin.create(44F) { builder ->
                            builder.inlinesEnabled(true)
                            builder.theme().inlinePadding(JLatexMathTheme.Padding.symmetric(24, 8))
                        })
                }
            }.textSetter(PrecomputedFutureTextSetterCompat.create()).build()

    private var markwon: Markwon = buildMarkwon()
    get() {
        val newCodeBlockBackground = resolveCodeBlockBackground()
        val newQuoteBarColor = resolveQuoteBarColor()
        var changed = false
        if (codeBlockBackground != newCodeBlockBackground) {
            codeBlockBackground = newCodeBlockBackground
            changed = true
        }
        if (quoteBarColor != newQuoteBarColor) {
            quoteBarColor = newQuoteBarColor
            changed = true
        }
        if (changed) {
            field = buildMarkwon()
        }
        return field
    }

    val plugins: List<MarkwonPlugin> = markwon.plugins

    fun invalidateColors() {
        markwon = buildMarkwon()
    }

    fun parse(text: String): Node {
        return markwon.parse(text)
    }

    /**
     * @param text the text you want to render
     * @param postProcessors an optional array of post processor to add any span if needed
     */
    fun render(text: String, vararg postProcessors: PostProcessor): CharSequence {
        return try {
            val parsed = markwon.parse(text)
            renderAndProcess(parsed, postProcessors)
        } catch (failure: Throwable) {
            Timber.v("Fail to render $text to html")
            text
        }
    }

    /**
     * @param node the node you want to render
     * @param postProcessors an optional array of post processor to add any span if needed
     */
    fun render(node: Node, vararg postProcessors: PostProcessor): CharSequence? {
        return try {
            renderAndProcess(node, postProcessors)
        } catch (failure: Throwable) {
            Timber.v("Fail to render $node to html")
            return null
        }
    }

    private fun renderAndProcess(node: Node, postProcessors: Array<out PostProcessor>): CharSequence {
        val renderedText = markwon.render(node).toSpannable()
        postProcessors.forEach {
            it.afterRender(renderedText)
        }
        return renderedText
    }
}

class MatrixHtmlPluginConfigure @Inject constructor(private val colorProvider: ColorProvider, private val resources: Resources) : HtmlPlugin.HtmlConfigure {

    override fun configureHtml(plugin: HtmlPlugin) {
        plugin
                .addHandler(FontTagHandler())
                .addHandler(ParagraphHandler(DimensionConverter(resources)))
                .addHandler(MxReplyTagHandler())
                .addHandler(CodePreTagHandler())
                .addHandler(CodeTagHandler())
                .addHandler(SpanHandler(colorProvider))
    }
}
