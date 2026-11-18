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
import com.example.tt.ui.formatBytes
import com.example.tt.ui.setupBottomNav
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tab "Ho so": doc/cap nhat profile qua FIREBASE FIRESTORE (Ky thuat 3),
 * kem thong ke tuc thi tu SQLite.
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

        setupBottomNav(binding.bottomNav, R.id.nav_profile)

        // 1) Hien thi ngay tu CACHE SQLite (nhanh)
        binding.tvName.text = user.name
        binding.tvEmail.text = user.email
        binding.tvMode.text = if (isOffline) {
            getString(R.string.mode_offline)
        } else {
            getString(R.string.mode_firebase)
        }
        binding.etNewName.setText(user.name)

        // 2) Thong ke tu SQLite
        lifecycleScope.launch {
            val downloads = withContext(Dispatchers.IO) { db.getAllDownloads() }
            binding.tvStatVideos.text = downloads.size.toString()
            binding.tvStatSize.text = formatBytes(downloads.sumOf { it.fileSize })
        }

        // 3) Lay du lieu MOI tu backend Firestore
        if (!isOffline) {
            binding.progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                profiles.getProfile(uid)
                    .onSuccess { p ->
                        if (p.name.isNotEmpty()) binding.tvName.text = p.name
                        binding.tvMode.text =
                            "${getString(R.string.mode_firebase)} · ${p.downloadCount} video"
                    }
                    .onFailure {
                        binding.tvMode.text =
                            "${getString(R.string.mode_firebase)} · offline"
                    }
                binding.progress.visibility = View.GONE
            }
        }

        // 4) CAP NHAT ten -> ghi len Firestore + cache SQLite
        binding.btnSaveName.setOnClickListener {
            val newName = binding.etNewName.text.toString().trim()
            if (newName.isEmpty()) {
                binding.tilNewName.error = getString(R.string.toast_need_name)
                return@setOnClickListener
            }
            binding.tilNewName.error = null
            setLoading(true)
            lifecycleScope.launch {
                if (!isOffline) {
                    profiles.saveProfile(uid, newName, user.email, "")
                        .onSuccess {
                            UserRepository(db).cacheProfile(uid, user.email, newName, "")
                            binding.tvName.text = newName
                            toast(getString(R.string.toast_saved_firestore))
                        }
                        .onFailure {
                            toast(getString(R.string.toast_firestore_error, it.message ?: ""))
                        }
                } else {
                    withContext(Dispatchers.IO) {
                        UserRepository(db).cacheProfile(uid, user.email, newName, "")
                    }
                    binding.tvName.text = newName
                    toast(getString(R.string.toast_saved_offline))
                }
                setLoading(false)
            }
        }

        // 5) DANG XUAT (co xac nhan)
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_logout_title)
                .setMessage(R.string.dialog_logout_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.action_logout) { _, _ ->
                    auth.logout()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finishAffinity()
                }
                .show()
        }
    }

    private fun setLoading(on: Boolean) {
        binding.progress.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnSaveName.isEnabled = !on
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
