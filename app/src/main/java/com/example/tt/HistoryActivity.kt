package com.example.tt

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tt.data.DatabaseHelper
import com.example.tt.data.DownloadRecord
import com.example.tt.databinding.ActivityHistoryBinding
import com.example.tt.ui.HistoryAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lich su tai video - trinh dien CRUD SQLite day du (Ky thuat 1):
 *   CREATE (da chen khi tai), READ (danh sach), UPDATE (yeu thich), DELETE (xoa)
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var db: DatabaseHelper
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = DatabaseHelper(this)

        adapter = HistoryAdapter(
            onToggleFavorite = { toggleFavorite(it) },
            onDelete = { delete(it) }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.btnClearAll.setOnClickListener { clearAll() }
        binding.btnBack.setOnClickListener { finish() }

        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { db.getAllDownloads() }
            adapter.submit(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.tvCount.text = "Tổng: ${'$'}{list.size} video · Yêu thích: ${'$'}{list.count { it.favorite == 1 }}"
        }
    }

    /** UPDATE: doi trang thai yeu thich */
    private fun toggleFavorite(rec: DownloadRecord) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.toggleFavorite(rec.id, rec.favorite == 0)
            }
            reload()
        }
    }

    /** DELETE: xoa 1 dong */
    private fun delete(rec: DownloadRecord) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.deleteDownload(rec.id) }
            toast("Đã xóa khỏi lịch sử")
            reload()
        }
    }

    /** DELETE: xoa tat ca */
    private fun clearAll() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.clearDownloads() }
            toast("Đã xóa toàn bộ lịch sử")
            reload()
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
