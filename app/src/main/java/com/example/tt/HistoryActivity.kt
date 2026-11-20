package com.example.tt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tt.data.DatabaseHelper
import com.example.tt.data.DownloadRecord
import com.example.tt.databinding.ActivityHistoryBinding
import com.example.tt.subtitle.SubtitleRepository
import com.example.tt.ui.HistoryAdapter
import com.example.tt.ui.MediaUri
import com.example.tt.ui.setupBottomNav
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tab "Lich su" - SQLite CRUD day du:
 *  - READ: danh sach + tim kiem + loc yeu thich
 *  - UPDATE: bat/tat yeu thich
 *  - DELETE: xoa 1 dong (co HOAN TAC) / xoa het (co xac nhan)
 *  - Chon ban phat: Goc / Co vietsub / Thuyet minh; sua vietsub & thuyet minh tai day.
 */
class HistoryActivity : AppCompatActivity() {

    private enum class PlayVariant(val key: String, val labelRes: Int) {
        ORIGINAL(PlayerActivity.VARIANT_ORIGINAL, R.string.player_source_original),
        VIETSUB(PlayerActivity.VARIANT_VIETSUB, R.string.player_source_vietsub),
        NARRATION(PlayerActivity.VARIANT_NARRATION, R.string.player_source_narration)
    }

    private data class MenuAction(val label: String, val run: () -> Unit)

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var db: DatabaseHelper
    private lateinit var adapter: HistoryAdapter

    private var allItems: List<DownloadRecord> = emptyList()
    private var searchQuery: String = ""
    private var favoriteOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = DatabaseHelper(this)
        setupBottomNav(binding.bottomNav, R.id.nav_history)

        adapter = HistoryAdapter(
            onOpen = { showPlaybackChooser(it) },
            onToggleFavorite = { toggleFavorite(it) },
            onDelete = { deleteWithUndo(it) },
            onSubtitle = { showProcessMenu(it) },
            onMore = { showProcessMenu(it) }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_clear_all) {
                confirmClearAll()
                true
            } else {
                false
            }
        }

        binding.etSearch.doAfterTextChanged {
            searchQuery = it?.toString().orEmpty()
            applyFilter()
        }

        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            favoriteOnly = checkedIds.contains(R.id.chipFavorite)
            applyFilter()
        }

        attachSwipeToDelete()
    }

    override fun onResume() {
        super.onResume()
        // Lan dau mo man + quay lai tu man Tai video -> cap nhat danh sach
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { db.getAllDownloads() }
            allItems = list
            applyFilter()
        }
    }

    private fun applyFilter() {
        val q = searchQuery.trim().lowercase()
        val filtered = allItems.filter { rec ->
            (!favoriteOnly || rec.favorite == 1) &&
                (q.isEmpty() ||
                    rec.title.lowercase().contains(q) ||
                    rec.author.lowercase().contains(q))
        }
        adapter.submitList(filtered)
        binding.layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.tvCount.text = getString(
            R.string.history_count,
            allItems.size,
            allItems.count { it.favorite == 1 }
        )
    }

    // ---------------- UPDATE: yeu thich ----------------
    private fun toggleFavorite(rec: DownloadRecord) {
        val newValue = rec.favorite == 0
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.toggleFavorite(rec.id, newValue) }
            toast(
                getString(
                    if (newValue) R.string.favorite_on else R.string.favorite_off
                )
            )
            reload()
        }
    }

    // ---------------- DELETE: 1 dong + hoan tac ----------------
    private fun deleteWithUndo(rec: DownloadRecord) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.deleteDownload(rec.id) }
            reload()

            Snackbar.make(binding.root, getString(R.string.toast_deleted_one), Snackbar.LENGTH_LONG)
                .setAction(R.string.toast_undo) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.insertDownload(rec) }
                        reload()
                    }
                }
                .show()
        }
    }

    // ---------------- DELETE: tat ca (co xac nhan) ----------------
    private fun confirmClearAll() {
        if (allItems.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_clear_title)
            .setMessage(getString(R.string.dialog_clear_message, allItems.size))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_clear_confirm) { _, _ -> clearAll() }
            .show()
    }

    private fun clearAll() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.clearDownloads() }
            toast(getString(R.string.toast_deleted_all))
            reload()
        }
    }

    // ---------------- Swipe de xoa ----------------
    private fun attachSwipeToDelete() {
        val callback = object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val rec = adapter.currentList.getOrNull(position) ?: return
                deleteWithUndo(rec)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.rvHistory)
    }

    // ---------------- Chon ban phat ----------------
    private fun hasSubtitle(rec: DownloadRecord): Boolean {
        val dbPath = rec.subtitlePath.isNotEmpty() && File(rec.subtitlePath).exists()
        return dbPath || File(SubtitleRepository(this).pathFor(rec.id)).exists()
    }

    private fun hasDub(rec: DownloadRecord): Boolean =
        rec.dubPath.isNotEmpty() &&
            runCatching { MediaUri.resolve(this, rec.dubPath) }.getOrNull() != null

    private fun showPlaybackChooser(rec: DownloadRecord) {
        val choices = buildList {
            add(PlayVariant.ORIGINAL to getString(PlayVariant.ORIGINAL.labelRes))
            if (hasSubtitle(rec)) {
                add(PlayVariant.VIETSUB to getString(PlayVariant.VIETSUB.labelRes))
            }
            if (hasDub(rec)) {
                add(PlayVariant.NARRATION to getString(PlayVariant.NARRATION.labelRes))
            }
        }
        // Chi co video goc -> mo thang, khong bat nguoi dung chon 1 muc.
        if (choices.size == 1) {
            openPlayer(rec, choices.first().first)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.player_source_prompt)
            .setItems(choices.map { it.second }.toTypedArray()) { _, which ->
                openPlayer(rec, choices[which].first)
            }
            .show()
    }

    private fun openPlayer(rec: DownloadRecord, variant: PlayVariant) {
        val targetPath = if (variant == PlayVariant.NARRATION) rec.dubPath else rec.filePath
        if (MediaUri.resolve(this, targetPath) == null) {
            toast(getString(R.string.toast_file_missing))
            return
        }

        val originalUri = MediaUri.resolve(this, rec.filePath)
        val dubUri = if (hasDub(rec)) MediaUri.resolve(this, rec.dubPath) else null
        val srtPath = SubtitleRepository(this).pathFor(rec.id)
            .takeIf { File(it).exists() }
            ?: rec.subtitlePath.takeIf { it.isNotBlank() && File(it).exists() }

        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_RECORD_ID, rec.id)
                originalUri?.let { putExtra(PlayerActivity.EXTRA_VIDEO_URI, it.toString()) }
                dubUri?.let { putExtra(PlayerActivity.EXTRA_DUB_URI, it.toString()) }
                if (variant != PlayVariant.ORIGINAL && srtPath != null) {
                    putExtra(PlayerActivity.EXTRA_SUBTITLE_URI, Uri.parse(srtPath))
                }
                putExtra(PlayerActivity.EXTRA_START_VARIANT, variant.key)
                putExtra(PlayerActivity.EXTRA_TITLE, rec.title)
            }
        )
    }

    // ---------------- Sua vietsub / thuyet minh ----------------
    /** Nhan "..." hoac nhan giu card: mo menu xu ly. */
    private fun showProcessMenu(rec: DownloadRecord) {
        val subtitleReady = hasSubtitle(rec)
        val dubReady = hasDub(rec)
        val actions = buildList {
            add(
                MenuAction(
                    getString(
                        if (subtitleReady) R.string.menu_edit_vietsub else R.string.menu_subtitle
                    )
                ) { openSubtitle(rec, withNarration = false) }
            )
            if (subtitleReady) {
                add(MenuAction(getString(R.string.action_create_narration)) {
                    openSubtitle(rec, withNarration = true)
                })
            }
            if (dubReady) {
                add(MenuAction(getString(R.string.menu_regenerate_narration)) {
                    openSubtitle(rec, withNarration = true)
                })
            }
            if (subtitleReady) {
                add(MenuAction(getString(R.string.action_open_player)) {
                    openPlayer(rec, PlayVariant.VIETSUB)
                })
            }
            if (dubReady) {
                add(MenuAction(getString(R.string.action_open_dub)) {
                    openPlayer(rec, PlayVariant.NARRATION)
                })
            }
            if (subtitleReady || dubReady) {
                add(MenuAction(getString(R.string.menu_delete_processed)) {
                    confirmDeleteProcessed(rec)
                })
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(rec.title)
            .setItems(actions.map { it.label }.toTypedArray()) { _, which ->
                actions[which].run()
            }
            .show()
    }

    /** Mo man hinh phu de de sua; khong tu chay TTS - nguoi dung bam nut. */
    private fun openSubtitle(rec: DownloadRecord, withNarration: Boolean) {
        if (rec.filePath.isEmpty()) {
            toast(getString(R.string.toast_file_missing))
            return
        }
        startActivity(
            SubtitleActivity.createIntent(
                this,
                rec.id,
                rec.platform.id,
                withNarration = withNarration,
                voiceLabel = rec.voice,
                keepBackground = true,
                autoRun = false
            )
        )
    }

    private fun confirmDeleteProcessed(rec: DownloadRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_processed_title)
            .setMessage(R.string.dialog_delete_processed_message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_delete_confirm) { _, _ -> deleteProcessed(rec) }
            .show()
    }

    /** Xoa file .srt + cau goc + WAV/MP4 thuyet minh, giu nguyen video goc. */
    private fun deleteProcessed(rec: DownloadRecord) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                SubtitleRepository(this@HistoryActivity).delete(rec.id)
                if (rec.dubPath.isNotBlank()) {
                    val mp4 = File(rec.dubPath)
                    runCatching { mp4.delete() }
                    mp4.parentFile?.let { dir -> runCatching { File(dir, "${rec.id}.wav").delete() } }
                }
                db.clearProcessed(rec.id)
            }
            toast(getString(R.string.toast_processed_deleted))
            reload()
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
