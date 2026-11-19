package com.example.tt.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.tt.R
import com.example.tt.data.DownloadRecord
import com.example.tt.databinding.ItemHistoryBinding

/**
 * Danh sach lich su tai (SQLite): mo video, yeu thich, xoa.
 * Dung ListAdapter + DiffUtil de animation muot va ho tro hoan tac khi xoa.
 */
class HistoryAdapter(
    private val onOpen: (DownloadRecord) -> Unit,
    private val onToggleFavorite: (DownloadRecord) -> Unit,
    private val onDelete: (DownloadRecord) -> Unit,
    private val onSubtitle: (DownloadRecord) -> Unit
) : ListAdapter<DownloadRecord, HistoryAdapter.VH>(DIFF) {

    class VH(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rec = getItem(position)
        with(holder.binding) {
            val context = root.context
            tvTitle.text = rec.title
            tvAuthor.text = rec.author
            val badges = buildList {
                if (rec.subtitlePath.isNotEmpty()) add(context.getString(R.string.badge_vietsub))
                if (rec.dubPath.isNotEmpty()) add(context.getString(R.string.badge_narration))
            }
            val badgeText = if (badges.isEmpty()) "" else " · " + badges.joinToString(" · ")
            tvMeta.text = "${rec.quality} · ${formatBytes(rec.fileSize)} · ${relativeTime(rec.createdAt)}$badgeText"

            imgCover.load(rec.coverUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_media_placeholder)
                error(R.drawable.bg_media_placeholder)
            }

            val isFavorite = rec.favorite == 1
            btnFavorite.setImageResource(
                if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            btnFavorite.setColorFilter(
                ContextCompat.getColor(
                    context,
                    if (isFavorite) R.color.tt_red else R.color.text_secondary
                )
            )

            root.setOnClickListener { onOpen(rec) }
            root.setOnLongClickListener {
                onSubtitle(rec)
                true
            }
            btnFavorite.setOnClickListener { onToggleFavorite(rec) }
            btnDelete.setOnClickListener { onDelete(rec) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DownloadRecord>() {
            override fun areItemsTheSame(oldItem: DownloadRecord, newItem: DownloadRecord): Boolean =
                oldItem.videoId == newItem.videoId

            override fun areContentsTheSame(oldItem: DownloadRecord, newItem: DownloadRecord): Boolean =
                oldItem == newItem
        }
    }
}
