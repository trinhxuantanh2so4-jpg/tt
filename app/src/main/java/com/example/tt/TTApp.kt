package com.example.tt

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.tt.network.dtk.DtkConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Application class: khoi tao Firebase tu dong qua google-services.json
 * (FirebaseApp.initializeApp() duoc goi ngam khi process khoi dong).
 *
 * Crash log cuc bo: exception chua duoc bat duoc ghi ra filesDir. O debug APK,
 * lan mo tiep theo se mo [CrashLogActivity] de nguoi dung doc/copy toan bo stack trace.
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
                File(filesDir, CRASH_FULL).writeText(stack)

                // Outer exception thuong chi la "Unable to instantiate activity";
                // root cause moi la thong tin co ich -> luu rieng cho toast.
                var root: Throwable = throwable
                while (root.cause != null && root.cause !== root) root = root.cause!!
                File(filesDir, CRASH_SUMMARY).writeText(root.toString())
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
        val message = runCatching { summary.readText().take(200) }.getOrDefault("không rõ")

        val isDebugApk = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebugApk) {
            // Debug APK: mo man hinh hien full stack trace de QA copy gui lai.
            runCatching {
                startActivity(
                    Intent(this, CrashLogActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } else {
            Toast.makeText(this, getString(R.string.crash_reported, message), Toast.LENGTH_LONG).show()
            clearCrashLog(this)
        }
    }

    companion object {
        const val CRASH_FULL = "crash_last.txt"
        private const val CRASH_SUMMARY = "crash_summary.txt"

        fun clearCrashLog(context: Context) {
            runCatching { File(context.filesDir, CRASH_SUMMARY).delete() }
            runCatching { File(context.filesDir, CRASH_FULL).delete() }
        }
    }
}
