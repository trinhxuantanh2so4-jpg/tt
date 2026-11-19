package com.example.tt

import android.content.ActivityNotFoundException
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
import com.example.tt.ui.HistoryAdapter
import com.example.tt.ui.MediaUri
import com.example.tt.ui.setupBottomNav
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tab "Lich su" - SQLite CRUD day du:
 *  - READ: danh sach + tim kiem + loc yeu thich
 *  - UPDATE: bat/tat yeu thich
 *  - DELETE: xoa 1 dong (co HOAN TAC) / xoa het (co xac nhan)
 *  - Mo lai video da tai bang FileProvider / MediaStore
 */
class HistoryActivity : AppCompatActivity() {

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
            onOpen = { openVideo(it) },
            onToggleFavorite = { toggleFavorite(it) },
            onDelete = { deleteWithUndo(it) },
            onSubtitle = { openSubtitle(it) }
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

    // ---------------- Mo lai video ----------------
    private fun openVideo(rec: DownloadRecord) {
        val videoUri = MediaUri.resolve(this, rec.filePath)

        if (videoUri != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(videoUri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // thu tiep bang link goc ben duoi
            } catch (_: SecurityException) {
                // File trong vung rieng cua app / het quyen truy cap
            }
        }

        val source = rec.sourceUrl.ifEmpty { rec.downloadUrl }
        if (source.startsWith("http")) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source))) }
                .onFailure { toast(getString(R.string.toast_no_app_open)) }
        } else {
            toast(getString(R.string.toast_file_missing))
        }
    }

    /** Nhan giu vao card de mo man hinh tao/chinh sua phu de. */
    private fun openSubtitle(rec: DownloadRecord) {
        if (rec.filePath.isEmpty()) {
            toast(getString(R.string.toast_file_missing))
            return
        }
        startActivity(SubtitleActivity.createIntent(this, rec.id, rec.platform.id))
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
