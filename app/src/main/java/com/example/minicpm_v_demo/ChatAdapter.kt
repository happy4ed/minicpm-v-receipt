package com.example.minicpm_v_demo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.noties.markwon.Markwon

class ChatAdapter(
    private val markwon: Markwon
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_WELCOME = 0
        private const val TYPE_USER = 1
        private const val TYPE_AI = 2
    }

    private var onSuggestionClick: ((String) -> Unit)? = null
    private var onStopClick: (() -> Unit)? = null

    private var activeAiHolder: AiMessageViewHolder? = null
    private var activeAiId: Long = -1L

    fun setOnSuggestionClick(listener: (String) -> Unit) {
        onSuggestionClick = listener
    }

    fun setOnStopClick(listener: () -> Unit) {
        onStopClick = listener
    }

    fun setActiveAiMessage(id: Long) {
        activeAiId = id
    }

    fun clearActiveAiMessage() {
        activeAiId = -1L
        activeAiHolder = null
    }

    fun updateStreamingText(id: Long, text: String) {
        if (id == activeAiId) {
            activeAiHolder?.updateText(text)
        }
    }

    fun setGeneratingDone(id: Long) {
        if (id == activeAiId) {
            activeAiHolder?.setStopButtonVisible(false)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ChatMessage.WelcomeCard -> TYPE_WELCOME
            is ChatMessage.UserMessage -> TYPE_USER
            is ChatMessage.AiMessage -> TYPE_AI
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_WELCOME -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_welcome_card, parent, false)
                WelcomeViewHolder(view)
            }
            TYPE_USER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_user_message, parent, false)
                UserMessageViewHolder(view)
            }
            TYPE_AI -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_ai_message, parent, false)
                AiMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatMessage.WelcomeCard -> (holder as WelcomeViewHolder).bind(item)
            is ChatMessage.UserMessage -> (holder as UserMessageViewHolder).bind(item)
            is ChatMessage.AiMessage -> {
                val aiHolder = holder as AiMessageViewHolder
                aiHolder.bind(item)
                if (item.id == activeAiId) {
                    activeAiHolder = aiHolder
                }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is AiMessageViewHolder && holder == activeAiHolder) {
            activeAiHolder = null
        }
    }

    inner class WelcomeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val btnSuggestion1: MaterialButton = itemView.findViewById(R.id.btn_suggestion_1)
        private val btnSuggestion2: MaterialButton = itemView.findViewById(R.id.btn_suggestion_2)

        fun bind(item: ChatMessage.WelcomeCard) {
            val ctx = itemView.context
            btnSuggestion1.text = ctx.getString(R.string.suggestion_1_label)
            btnSuggestion2.text = ctx.getString(R.string.suggestion_2_label)
            btnSuggestion1.setOnClickListener {
                onSuggestionClick?.invoke(ctx.getString(R.string.suggestion_1_prompt))
            }
            btnSuggestion2.setOnClickListener {
                onSuggestionClick?.invoke(ctx.getString(R.string.suggestion_2_prompt))
            }
        }
    }

    inner class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(R.id.tv_user_text)
        private val flImageContainer: View = itemView.findViewById(R.id.fl_user_image_container)
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_user_image)
        private val ivVideoBadge: ImageView = itemView.findViewById(R.id.iv_video_play_badge)
        private val tvImageInfo: TextView = itemView.findViewById(R.id.tv_image_info)
        private val progressImage: LinearProgressIndicator = itemView.findViewById(R.id.progress_image)

        fun bind(item: ChatMessage.UserMessage) {
            tvText.text = item.text
            tvText.visibility = if (item.text.isNotBlank()) View.VISIBLE else View.GONE

            if (item.imageBitmap != null) {
                ivImage.setImageBitmap(item.imageBitmap)
                flImageContainer.visibility = View.VISIBLE
                ivVideoBadge.visibility = if (item.isVideo) View.VISIBLE else View.GONE
                tvImageInfo.visibility = View.VISIBLE
                tvImageInfo.text = item.imageInfo ?: ""
                progressImage.visibility = if (item.isPrefilling) View.VISIBLE else View.GONE
            } else {
                flImageContainer.visibility = View.GONE
                ivVideoBadge.visibility = View.GONE
                tvImageInfo.visibility = View.GONE
                progressImage.visibility = View.GONE
            }
        }
    }

    inner class AiMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(R.id.tv_ai_text)
        private val btnStop: MaterialButton = itemView.findViewById(R.id.btn_stop_generating)
        private val btnCopy: MaterialButton = itemView.findViewById(R.id.btn_copy)
        private var currentText: String = ""

        fun bind(item: ChatMessage.AiMessage) {
            currentText = item.text
            renderMarkdown(item.text)
            btnStop.visibility = if (item.isGenerating) View.VISIBLE else View.GONE
            btnCopy.visibility = if (!item.isGenerating && item.text.isNotBlank()) View.VISIBLE else View.GONE
            btnStop.setOnClickListener { onStopClick?.invoke() }
            btnCopy.setOnClickListener {
                val ctx = itemView.context
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("AI Response", currentText))
                Toast.makeText(ctx, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        }

        fun updateText(text: String) {
            currentText = text
            renderMarkdown(text)
        }

        fun setStopButtonVisible(visible: Boolean) {
            btnStop.visibility = if (visible) View.VISIBLE else View.GONE
            if (!visible && currentText.isNotBlank()) {
                btnCopy.visibility = View.VISIBLE
            }
        }

        // Re-rendering Markwon on every streaming token works fine in practice
        // (parsing a few KB of partial markdown is sub-millisecond). If the
        // generated text gets very long we can add a 100-150ms throttle here.
        //
        // [MarkdownEscape.normalizeResponseText] is the only place where
        // assistant output is touched for v4.6's literal `\n` artefact.
        // It runs at the rendering boundary so the canonical text stored in
        // [ChatMessage.AiMessage.text] (and any future re-feed into the model)
        // remains byte-identical to what native produced.
        private fun renderMarkdown(text: String) {
            markwon.setMarkdown(tvText, MarkdownEscape.normalizeResponseText(text))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return when {
                oldItem is ChatMessage.UserMessage && newItem is ChatMessage.UserMessage ->
                    oldItem.text == newItem.text &&
                            oldItem.imageBitmap == newItem.imageBitmap &&
                            oldItem.imageInfo == newItem.imageInfo &&
                            oldItem.isPrefilling == newItem.isPrefilling &&
                            oldItem.isVideo == newItem.isVideo
                oldItem is ChatMessage.AiMessage && newItem is ChatMessage.AiMessage ->
                    oldItem.isGenerating == newItem.isGenerating &&
                            (oldItem.isGenerating || oldItem.text == newItem.text)
                oldItem is ChatMessage.WelcomeCard && newItem is ChatMessage.WelcomeCard -> true
                else -> false
            }
        }
    }
}
