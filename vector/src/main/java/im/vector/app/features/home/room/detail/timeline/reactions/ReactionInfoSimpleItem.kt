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

package im.vector.app.features.home.room.detail.timeline.reactions

import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.airbnb.epoxy.EpoxyModelWithHolder
import im.vector.app.R
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.glide.renderReactionImage
import im.vector.app.core.utils.DimensionConverter
import im.vector.lib.core.utils.epoxy.charsequence.EpoxyCharSequence

/**
 * Item displaying an emoji reaction (single line with emoji, author, time).
 */
@EpoxyModelClass(layout = R.layout.item_simple_reaction_info)
abstract class ReactionInfoSimpleItem : EpoxyModelWithHolder<ReactionInfoSimpleItem.Holder>() {

    @EpoxyAttribute
    lateinit var reactionKey: EpoxyCharSequence

    @EpoxyAttribute
    var reactionUrl: String? = null

    @EpoxyAttribute
    lateinit var authorDisplayName: String

    @EpoxyAttribute
    var timeStamp: String? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var userClicked: ClickListener? = null

    @EpoxyAttribute
    lateinit var dimensionConverter: DimensionConverter

    @EpoxyAttribute
    lateinit var activeSessionHolder: ActiveSessionHolder

    override fun bind(holder: Holder) {
        super.bind(holder)
        holder.emojiReactionView.text = reactionKey.charSequence
        holder.displayNameView.text = authorDisplayName
        timeStamp?.let {
            holder.timeStampView.text = it
            holder.timeStampView.isVisible = true
        } ?: run {
            holder.timeStampView.isVisible = false
        }
        holder.view.onClick(userClicked)

        activeSessionHolder.getSafeActiveSession()?.let { session ->
            val size = dimensionConverter.dpToPx(16)
            renderReactionImage(reactionUrl, reactionKey.charSequence.toString(), size, session, holder.emojiReactionView, holder.emojiReactionImageView)
        }
    }

    class Holder : VectorEpoxyHolder() {
        val emojiReactionView by bind<TextView>(R.id.itemSimpleReactionInfoKey)
        val emojiReactionImageView by bind<ImageView>(R.id.itemSimpleReactionInfoImage)
        val displayNameView by bind<TextView>(R.id.itemSimpleReactionInfoMemberName)
        val timeStampView by bind<TextView>(R.id.itemSimpleReactionInfoTime)
    }
}
