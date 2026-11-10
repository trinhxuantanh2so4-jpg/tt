package com.example.tt.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.tt.R
import com.example.tt.data.DownloadRecord
import com.example.tt.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter danh sach lich su tai (SQLite) - co nut yeu thich & xoa.
 */
class HistoryAdapter(
    private val onToggleFavorite: (DownloadRecord) -> Unit,
    private val onDelete: (DownloadRecord) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val items = mutableListOf<DownloadRecord>()

    fun submit(list: List<DownloadRecord>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rec = items[position]
        with(holder.binding) {
            tvTitle.text = rec.title
            tvAuthor.text = "👤 ${'$'}{rec.author}"
            tvSize.text = "${'$'}{rec.quality} · ${'$'}{rec.fileSize / 1024 / 1024} MB · ${'$'}{" +
                SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(rec.createdAt))
            }"
            imgCover.load(rec.coverUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_launcher_foreground)
            }
            btnFavorite.text = if (rec.favorite == 1) "❤️" else "🤍"
            btnFavorite.setOnClickListener { onToggleFavorite(rec) }
            btnDelete.setOnClickListener { onDelete(rec) }
        }
    }
}
