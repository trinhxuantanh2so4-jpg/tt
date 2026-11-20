package com.example.tt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tt.databinding.ActivityCrashLogBinding
import java.io.File

/**
 * Hien toan bo stack trace cua lan crash gan nhat ngay tren may (debug APK).
 * Nguoi dung chi can bam "Sao chep log" va dan vao bao cao loi.
 */
class CrashLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrashLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        val logFile = File(filesDir, TTApp.CRASH_FULL)
        val content = runCatching { logFile.readText() }
            .getOrDefault(getString(R.string.crash_log_empty))
        binding.tvCrashLog.text = content

        binding.btnCopyCrash.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null) {
                Toast.makeText(this, R.string.crash_log_copy_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", content))
            Toast.makeText(this, R.string.crash_log_copied, Toast.LENGTH_SHORT).show()
        }

        binding.btnCloseCrash.setOnClickListener {
            TTApp.clearCrashLog(this)
            finish()
        }
    }
}
