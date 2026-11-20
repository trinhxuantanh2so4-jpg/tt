package com.example.tt.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.tt.R
import com.example.tt.databinding.ItemSubtitleCueBinding
import com.example.tt.subtitle.SubtitleCue

/**
 * Danh sach cue cua file .srt dang duoc tao - moi cue co TextInput de nguoi dung
 * sua cau dich (tieng Viet) va giu nguyen cau goc.
 */
/**
 * @param onTextChanged nhận `cue.index` (ổn định) chứ không phải adapter position,
 *                      tránh ghi nhầm khi ViewHolder được tái sử dụng.
 */
class SubtitleAdapter(
    private val onTextChanged: (Int, String) -> Unit
) : RecyclerView.Adapter<SubtitleAdapter.VH>() {

    private val items = mutableListOf<SubtitleCue>()

    fun submitList(newItems: List<SubtitleCue>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(
        val binding: ItemSubtitleCueBinding,
        private val onCueTextChanged: (Int, String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private var watcher: TextWatcher? = null

        fun bind(cue: SubtitleCue, position: Int) {
            binding.tvCueTime.text =
                "%02d:%02d --> %02d:%02d".format(
                    (cue.startMs / 60_000).toInt(),
                    ((cue.startMs % 60_000) / 1000).toInt(),
                    (cue.endMs / 60_000).toInt(),
                    ((cue.endMs % 60_000) / 1000).toInt()
                )
            binding.tvCueSource.text = cue.source.ifEmpty { cue.text }
            binding.etCueText.hint = binding.root.context.getString(R.string.subtitle_edit_hint)

            // QUAN TRỌNG: remove watcher CŨ trước khi setText. Nếu làm ngược lại,
            // watcher của cue trước đó (ViewHolder tái sử dụng) sẽ chạy và ghi đè nhầm cue.
            watcher?.let { binding.etCueText.removeTextChangedListener(it) }
            watcher = null
            binding.etCueText.setText(cue.text)

            // Dùng cue.index làm khoá ổn định thay vì adapter position.
            val cueIndex = cue.index
            watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    onCueTextChanged(cueIndex, s?.toString().orEmpty())
                }
            }
            watcher?.let { binding.etCueText.addTextChangedListener(it) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            ItemSubtitleCueBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onTextChanged
        )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position], position)

    override fun getItemCount(): Int = items.size
}
