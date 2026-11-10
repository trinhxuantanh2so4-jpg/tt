package com.example.tt.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.tt.R
import com.example.tt.databinding.ItemTrendingBinding
import com.example.tt.network.TikTokVideo

/**
 * Adapter hien thi danh sach video thinh hanh lay tu API (Ky thuat 2).
 */
class TrendingAdapter(
    private val onClick: (TikTokVideo) -> Unit
) : RecyclerView.Adapter<TrendingAdapter.VH>() {

    private val items = mutableListOf<TikTokVideo>()

    fun submit(list: List<TikTokVideo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemTrendingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTrendingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = items[position]
        with(holder.binding) {
            tvTitle.text = v.title ?: "Video TikTok"
            val authorName = v.author?.nickname ?: "Không rõ"
            tvAuthor.text = "👤 $authorName"
            imgCover.load(v.cover) {
                crossfade(true)
                placeholder(R.drawable.ic_launcher_foreground)
            }
            root.setOnClickListener { onClick(v) }
        }
    }
}
