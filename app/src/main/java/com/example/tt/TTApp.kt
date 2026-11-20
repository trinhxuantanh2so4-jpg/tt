package com.example.tt

import android.app.Application
import android.widget.Toast
import com.example.tt.network.dtk.DtkConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Application class: khoi tao Firebase tu dong qua google-services.json
 * (FirebaseApp.initializeApp() duoc goi ngam khi process khoi dong).
 *
 * Them crash log cuc bo: moi exception chua duoc bat se duoc ghi ra filesDir
 * de co the bao cao exact stack trace thay vi chi noi "app tu thoat".
 */
class TTApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Cấu hình API DTK lưu trên máy; mặc định rỗng → dùng nguồn cũ.
        DtkConfig.init(this)
        installCrashLog()
        reportLastCrash()
    }

    private fun installCrashLog() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
                File(filesDir, CRASH_FULL).writeText("${System.currentTimeMillis()}\n$stack")
                File(filesDir, CRASH_SUMMARY).writeText(throwable.toString())
            }
            // Van giu hanh vi mac dinh (kill process) de khong giu app o trang thai loi.
            previous?.uncaughtException(thread, throwable)
                ?: run {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(1)
                }
        }
    }

    private fun reportLastCrash() {
        val summary = File(filesDir, CRASH_SUMMARY)
        if (!summary.exists()) return
        val message = runCatching { summary.readText().take(160) }.getOrDefault("không rõ")
        runCatching { summary.delete() }
        runCatching { File(filesDir, CRASH_FULL).delete() }
        Toast.makeText(this, getString(R.string.crash_reported, message), Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val CRASH_FULL = "crash_last.txt"
        private const val CRASH_SUMMARY = "crash_summary.txt"
    }
}
