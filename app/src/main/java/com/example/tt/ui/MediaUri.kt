package com.example.tt.ui

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Giai quyet Uri cua video da tai (SAF / file noi bo / MediaStore). */
object MediaUri {

    fun resolve(context: Context, filePath: String): Uri? {
        if (filePath.isEmpty()) return null

        // 1) File nguoi dung chon qua SAF
        if (filePath.startsWith("content://")) {
            return runCatching { Uri.parse(filePath) }.getOrNull()
        }

        // 2) File trong bo nho may (Android 8-9)
        val file = File(filePath)
        if (file.exists()) {
            return runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()
        }

        // 3) Video luu qua MediaStore: "Download/TikTok/ten.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && filePath.startsWith("Download/")) {
            return findInMediaStore(context, filePath.substringAfterLast('/'))
        }
        return null
    }

    private fun findInMediaStore(context: Context, displayName: String): Uri? {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection, selection, arrayOf(displayName), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0)
                )
            }
        }
        return null
    }
}
