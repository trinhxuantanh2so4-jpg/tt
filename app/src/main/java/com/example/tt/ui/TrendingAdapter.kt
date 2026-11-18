package com.example.tt.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.tt.R
import com.example.tt.databinding.ItemTrendingBinding
import com.example.tt.network.TikTokVideo

/**
 * Grid video thinh hanh (2 cot): anh 9:16, overlay tieu de + tac gia, thoi luong.
 */
class TrendingAdapter(
    private val onClick: (TikTokVideo) -> Unit
) : ListAdapter<TikTokVideo, TrendingAdapter.VH>(DIFF) {

    class VH(val binding: ItemTrendingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTrendingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val video = getItem(position)
        with(holder.binding) {
            tvTitle.text = video.title?.takeIf { it.isNotBlank() }
                ?: root.context.getString(R.string.nav_trending)
            tvAuthor.text = video.author?.nickname ?: video.author?.uniqueId ?: "TikTok"

            val duration = formatDuration(video.duration)
            tvDuration.text = duration
            tvDuration.visibility =
                if (duration.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE

            imgCover.load(video.cover) {
                crossfade(true)
                placeholder(R.drawable.bg_media_placeholder)
                error(R.drawable.bg_media_placeholder)
            }
            root.setOnClickListener { onClick(video) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TikTokVideo>() {
            override fun areItemsTheSame(oldItem: TikTokVideo, newItem: TikTokVideo): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TikTokVideo, newItem: TikTokVideo): Boolean =
                oldItem == newItem
        }
    }
}
