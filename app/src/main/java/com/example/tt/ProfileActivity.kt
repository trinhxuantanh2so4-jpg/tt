package com.example.tt

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tt.data.DatabaseHelper
import com.example.tt.data.UserRepository
import com.example.tt.databinding.ActivityProfileBinding
import com.example.tt.firebase.AuthRepository
import com.example.tt.firebase.ProfileRepository
import kotlinx.coroutines.launch

/**
 * Ho so nguoi dung - doc/cap nhat qua FIREBASE FIRESTORE (Ky thuat 3),
 * kem cache SQLite de hien thi tuc thi.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = DatabaseHelper(this)
        val auth = AuthRepository(UserRepository(db))
        val profiles = ProfileRepository()

        val user = auth.currentUser() ?: run {
            finish(); return
        }
        val uid = user.uid
        val isOffline = uid.startsWith("offline-")

        // 1) Hien thi ngay tu CACHE SQLite (nhanh)
        binding.tvName.text = user.name
        binding.tvEmail.text = user.email
        binding.tvMode.text = if (isOffline) "Chế độ offline (SQLite)" else "Firebase Auth ✓"
        binding.etNewName.setText(user.name)
        binding.tvDownloads.text = "…"

        // 2) Lay du lieu MOI tu backend Firestore
        if (!isOffline) {
            binding.progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                profiles.getProfile(uid)
                    .onSuccess { p ->
                        if (p.name.isNotEmpty()) binding.tvName.text = p.name
                        binding.tvDownloads.text = "${p.downloadCount} video đã tải 📥"
                    }
                    .onFailure { binding.tvDownloads.text = "Không kết nối được Firestore" }
                binding.progress.visibility = View.GONE
            }
        } else {
            binding.tvDownloads.text = "${db.countDownloads()} video đã tải 📥 (SQLite)"
        }

        // 3) CAP NHAT ten -> ghi len Firestore + cache SQLite
        binding.btnSaveName.setOnClickListener {
            val newName = binding.etNewName.text.toString().trim()
            if (newName.isEmpty()) { toast("Nhập tên mới"); return@setOnClickListener }
            setLoading(true)
            lifecycleScope.launch {
                if (!isOffline) {
                    profiles.saveProfile(uid, newName, user.email, "")
                        .onSuccess {
                            UserRepository(db).cacheProfile(uid, user.email, newName, "")
                            binding.tvName.text = newName
                            toast("Đã lưu lên Firestore ✓")
                        }
                        .onFailure { toast("Lỗi Firestore: ${it.message}") }
                } else {
                    UserRepository(db).cacheProfile(uid, user.email, newName, "")
                    binding.tvName.text = newName
                    toast("Đã lưu (offline)")
                }
                setLoading(false)
            }
        }

        // 4) DANG XUAT
        binding.btnLogout.setOnClickListener {
            auth.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
    }

    private fun setLoading(on: Boolean) {
        binding.progress.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnSaveName.isEnabled = !on
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
