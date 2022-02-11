/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.home.room.detail.timeline.item

import android.content.Context
import android.text.Spanned
import android.text.TextUtils
import android.text.method.MovementMethod
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.PrecomputedTextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.epoxy.onLongClickIgnoringLinks
import im.vector.app.core.ui.views.FooteredTextView
import im.vector.app.features.home.room.detail.timeline.TimelineEventController
import im.vector.app.features.home.room.detail.timeline.tools.findPillsAndProcess
import im.vector.app.features.home.room.detail.timeline.url.PreviewUrlRetriever
import im.vector.app.features.home.room.detail.timeline.url.PreviewUrlUiState
import im.vector.app.features.home.room.detail.timeline.url.PreviewUrlViewSc
import im.vector.app.features.media.ImageContentRenderer
import im.vector.lib.core.utils.epoxy.charsequence.EpoxyCharSequence
import io.noties.markwon.MarkwonPlugin
import org.matrix.android.sdk.api.extensions.orFalse

@EpoxyModelClass(layout = R.layout.item_timeline_event_base)
abstract class MessageTextItem : AbsMessageItem<MessageTextItem.Holder>() {

    @EpoxyAttribute
    var searchForPills: Boolean = false

    @EpoxyAttribute
    var message: EpoxyCharSequence? = null

    @EpoxyAttribute
    var bindingOptions: BindingOptions? = null

    @EpoxyAttribute
    var useBigFont: Boolean = false

    @EpoxyAttribute
    var previewUrlRetriever: PreviewUrlRetriever? = null

    @EpoxyAttribute
    var previewUrlCallback: TimelineEventController.PreviewUrlCallback? = null

    @EpoxyAttribute
    var imageContentRenderer: ImageContentRenderer? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var movementMethod: MovementMethod? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var markwonPlugins: (List<MarkwonPlugin>)? = null

    private val previewUrlViewUpdater = PreviewUrlViewUpdater()

    // Remember footer measures for URL updates
    private var footerWidth: Int = 0
    private var footerHeight: Int = 0

    override fun bind(holder: Holder) {
        // Preview URL
        previewUrlViewUpdater.holder = holder
        previewUrlViewUpdater.previewUrlView = holder.previewUrlView
        previewUrlViewUpdater.imageContentRenderer = imageContentRenderer
        val safePreviewUrlRetriever = previewUrlRetriever
        if (safePreviewUrlRetriever == null) {
            holder.previewUrlView.isVisible = false
        } else {
            safePreviewUrlRetriever.addListener(attributes.informationData.eventId, previewUrlViewUpdater)
        }
        holder.previewUrlView.delegate = previewUrlCallback

        if (useBigFont) {
            holder.messageView.textSize = 44F
        } else {
            holder.messageView.textSize = 14F
        }
        if (searchForPills) {
            message?.charSequence?.findPillsAndProcess(coroutineScope) {
                // mmm.. not sure this is so safe in regards to cell reuse
                it.bind(holder.messageView)
            }
        }

        message?.charSequence.let { charSequence ->
            markwonPlugins?.forEach { plugin -> plugin.beforeSetText(holder.messageView, charSequence as Spanned) }
        }
        super.bind(holder)
        holder.messageView.movementMethod = movementMethod
        renderSendState(holder.messageView, holder.messageView)
        holder.messageView.onClick(attributes.itemClickListener)
        holder.messageView.onLongClickIgnoringLinks(attributes.itemLongClickListener)
        holder.messageView.setTextWithEmojiSupport(message?.charSequence, bindingOptions)
        markwonPlugins?.forEach { plugin -> plugin.afterSetText(holder.messageView) }
    }

    private fun AppCompatTextView.setTextWithEmojiSupport(message: CharSequence?, bindingOptions: BindingOptions?) {
        if (bindingOptions?.canUseTextFuture.orFalse() && message != null) {
            val textFuture = PrecomputedTextCompat.getTextFuture(message, TextViewCompat.getTextMetricsParams(this), null)
            setTextFuture(textFuture)
        } else {
            // Remove possible previously set futures that might overwrite our text
            setTextFuture(null)

            text = message
        }
    }

    override fun unbind(holder: Holder) {
        super.unbind(holder)
        previewUrlViewUpdater.previewUrlView = null
        previewUrlViewUpdater.imageContentRenderer = null
        previewUrlRetriever?.removeListener(attributes.informationData.eventId, previewUrlViewUpdater)
    }

    override fun getViewType() = STUB_ID

    class Holder : AbsMessageItem.Holder(STUB_ID) {
        val messageView by bind<FooteredTextView>(R.id.messageTextView)
        val previewUrlView by bind<PreviewUrlViewSc>(R.id.messageUrlPreview)
    }

    inner class PreviewUrlViewUpdater : PreviewUrlRetriever.PreviewUrlRetrieverListener {
        var previewUrlView: PreviewUrlViewSc? = null
        var holder: Holder? = null
        var imageContentRenderer: ImageContentRenderer? = null

        override fun onStateUpdated(state: PreviewUrlUiState) {
            val safeImageContentRenderer = imageContentRenderer
            if (safeImageContentRenderer == null) {
                previewUrlView?.isVisible = false
                return
            }
            previewUrlView?.render(state, safeImageContentRenderer)

            ///* // disabled for now: just set all in reserveFooterSpace to ensure space in all scenarios
            // Currently, all states except data imply hidden preview
            if (state is PreviewUrlUiState.Data) {
                // Don't reserve footer space in message view, but preview view
                holder?.messageView?.footerWidth = 0
                holder?.messageView?.footerHeight = 0
            } else {
                holder?.messageView?.footerWidth = footerWidth
                holder?.messageView?.footerHeight = footerHeight
            }
            //holder?.messageView?.invalidate()
            holder?.messageView?.requestLayout()
            //*/
        }
    }

    companion object {
        private const val STUB_ID = R.id.messageContentTextStub
    }

    override fun messageBubbleAllowed(context: Context): Boolean {
        return true
    }

    override fun allowFooterOverlay(holder: Holder): Boolean {
        return true
    }

    override fun needsFooterReservation(holder: Holder): Boolean {
        return true
    }

    override fun reserveFooterSpace(holder: Holder, width: Int, height: Int) {
        // Remember for PreviewUrlViewUpdater.onStateUpdated
        footerWidth = width
        footerHeight = height
        // Reserve both in preview and in message
        // User might close preview, so we still need place in the message
        // if we don't want to change this afterwards
        // This might be a race condition, but the UI-isssue if evaluated wrongly is negligible
        if (!holder.previewUrlView.isVisible) {
            holder.messageView.footerWidth = width
            holder.messageView.footerHeight = height
        } // else: will be handled in onStateUpdated
        holder.previewUrlView.footerWidth = height
        holder.previewUrlView.footerHeight = height
        holder.previewUrlView.updateFooterSpace()
    }
}
