package com.example.tt.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.tt.network.VideoPlatform

/**
 * KY THUAT 1 - SQLITE
 * Quan ly toan bo CSDL local cua app:
 *  - bang downloads : lich su tai video (CRUD day du)
 *  - bang users     : cache profile tu Firebase + tai khoan offline du phong
 *  - bang sessions  : phien dang nhap hien tai
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "tt_downloader.db"
        private const val DATABASE_VERSION = 2

        // ---- Bang downloads ----
        const val T_DOWNLOADS = "downloads"
        const val C_ID = "id"
        const val C_VIDEO_ID = "video_id"
        const val C_TITLE = "title"
        const val C_AUTHOR = "author"
        const val C_COVER = "cover_url"
        const val C_SOURCE_URL = "source_url"
        const val C_DL_URL = "download_url"
        const val C_QUALITY = "quality"
        const val C_FILE_PATH = "file_path"
        const val C_FILE_SIZE = "file_size"
        const val C_FAVORITE = "favorite"
        const val C_CREATED_AT = "created_at"
        // Phase B: phu de + nguon video
        const val C_PLATFORM = "platform"
        const val C_LANGUAGE = "language"
        const val C_SUBTITLE_PATH = "subtitle_path"
        const val C_PROCESS_STATUS = "process_status"

        // ---- Bang users (cache profile + offline auth) ----
        const val T_USERS = "users"
        const val C_UID = "uid"
        const val C_EMAIL = "email"
        const val C_NAME = "name"
        const val C_AVATAR = "avatar"
        const val C_PASSWORD_HASH = "password_hash"
        const val C_IS_OFFLINE = "is_offline" // 1 = tai khoan offline (du phong)
        const val C_LAST_LOGIN = "last_login"

        // ---- Bang sessions ----
        const val T_SESSIONS = "sessions"
        const val C_SESSION_UID = "uid"
        const val C_LOGGED_IN = "logged_in"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Index giup tim kiem nhanh theo video_id
        db.execSQL(
            """
            CREATE TABLE $T_DOWNLOADS (
                $C_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $C_VIDEO_ID TEXT NOT NULL,
                $C_TITLE TEXT, $C_AUTHOR TEXT, $C_COVER TEXT,
                $C_SOURCE_URL TEXT, $C_DL_URL TEXT, $C_QUALITY TEXT,
                $C_FILE_PATH TEXT, $C_FILE_SIZE INTEGER DEFAULT 0,
                $C_FAVORITE INTEGER DEFAULT 0,
                $C_CREATED_AT INTEGER NOT NULL,
                $C_PLATFORM TEXT DEFAULT 'tiktok',
                $C_LANGUAGE TEXT DEFAULT '',
                $C_SUBTITLE_PATH TEXT DEFAULT '',
                $C_PROCESS_STATUS TEXT DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX idx_downloads_video ON $T_DOWNLOADS ($C_VIDEO_ID)"
        )

        db.execSQL(
            """
            CREATE TABLE $T_USERS (
                $C_UID TEXT PRIMARY KEY,
                $C_EMAIL TEXT UNIQUE NOT NULL,
                $C_NAME TEXT, $C_AVATAR TEXT,
                $C_PASSWORD_HASH TEXT,
                $C_IS_OFFLINE INTEGER DEFAULT 0,
                $C_LAST_LOGIN INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $T_SESSIONS (
                $C_SESSION_UID TEXT PRIMARY KEY,
                $C_LOGGED_IN INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Giữ nguyên dữ liệu cũ, chỉ thêm cột cho Phase B.
            addColumnIfMissing(db, C_PLATFORM, "TEXT DEFAULT 'tiktok'")
            addColumnIfMissing(db, C_LANGUAGE, "TEXT DEFAULT ''")
            addColumnIfMissing(db, C_SUBTITLE_PATH, "TEXT DEFAULT ''")
            addColumnIfMissing(db, C_PROCESS_STATUS, "TEXT DEFAULT ''")
        }
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, column: String, definition: String) {
        val columns = db.rawQuery("PRAGMA table_info($T_DOWNLOADS)", null).use { c ->
            val idx = c.getColumnIndex("name")
            val names = mutableListOf<String>()
            while (c.moveToNext()) names.add(c.getString(idx) ?: "")
            names
        }
        if (column !in columns) {
            db.execSQL("ALTER TABLE $T_DOWNLOADS ADD COLUMN $column $definition")
        }
    }

    // ================= DOWNLOADS: CRUD =================

    /** INSERT - co tranh chap thi REPLACE (cung video tai lai) */
    fun insertDownload(rec: DownloadRecord): Long {
        val cv = ContentValues().apply {
            put(C_VIDEO_ID, rec.videoId)
            put(C_TITLE, rec.title)
            put(C_AUTHOR, rec.author)
            put(C_COVER, rec.coverUrl)
            put(C_SOURCE_URL, rec.sourceUrl)
            put(C_DL_URL, rec.downloadUrl)
            put(C_QUALITY, rec.quality)
            put(C_FILE_PATH, rec.filePath)
            put(C_FILE_SIZE, rec.fileSize)
            put(C_FAVORITE, rec.favorite)
            put(C_CREATED_AT, rec.createdAt)
            put(C_PLATFORM, rec.platform.id)
            put(C_LANGUAGE, rec.language)
            put(C_SUBTITLE_PATH, rec.subtitlePath)
            put(C_PROCESS_STATUS, rec.processStatus)
        }
        writableDatabase.use { db ->
            return db.insertWithOnConflict(
                T_DOWNLOADS, null, cv, SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    /** SELECT tat ca, moi nhat truoc */
    fun getAllDownloads(): List<DownloadRecord> = queryDownloads(null)

    /** SELECT 1 dong theo id (dung cho man hinh phu de). */
    fun getDownload(id: Long): DownloadRecord? =
        queryDownloads("$C_ID = ?", arrayOf(id.toString())).firstOrNull()

    /** SELECT chi muc yeu thich */
    fun getFavorites(): List<DownloadRecord> = queryDownloads("$C_FAVORITE = 1")

    private fun queryDownloads(where: String?): List<DownloadRecord> {
        val list = mutableListOf<DownloadRecord>()
        readableDatabase.use { db ->
            db.query(
                T_DOWNLOADS, null, where, null, null, null,
                "$C_CREATED_AT DESC"
            ).use { c ->
                while (c.moveToNext()) {
                    list.add(
                        DownloadRecord(
                            id = c.getLong(c.getColumnIndexOrThrow(C_ID)),
                            videoId = c.getString(c.getColumnIndexOrThrow(C_VIDEO_ID)),
                            title = c.getString(c.getColumnIndexOrThrow(C_TITLE)) ?: "",
                            author = c.getString(c.getColumnIndexOrThrow(C_AUTHOR)) ?: "",
                            coverUrl = c.getString(c.getColumnIndexOrThrow(C_COVER)) ?: "",
                            sourceUrl = c.getString(c.getColumnIndexOrThrow(C_SOURCE_URL)) ?: "",
                            downloadUrl = c.getString(c.getColumnIndexOrThrow(C_DL_URL)) ?: "",
                            quality = c.getString(c.getColumnIndexOrThrow(C_QUALITY)) ?: "",
                            filePath = c.getString(c.getColumnIndexOrThrow(C_FILE_PATH)) ?: "",
                            fileSize = c.getLong(c.getColumnIndexOrThrow(C_FILE_SIZE)),
                            favorite = c.getInt(c.getColumnIndexOrThrow(C_FAVORITE)),
                            createdAt = c.getLong(c.getColumnIndexOrThrow(C_CREATED_AT)),
                            platform = runCatching {
                                VideoPlatform.fromId(c.getString(c.getColumnIndexOrThrow(C_PLATFORM)))
                            }.getOrDefault(VideoPlatform.TIKTOK),
                            language = c.getStringOrEmpty(C_LANGUAGE),
                            subtitlePath = c.getStringOrEmpty(C_SUBTITLE_PATH),
                            processStatus = c.getStringOrEmpty(C_PROCESS_STATUS)
                        )
                    )
                }
            }
        }
        return list
    }

    /** UPDATE - doi trang thai yeu thich */
    fun toggleFavorite(id: Long, favorite: Boolean): Int {
        val cv = ContentValues().apply { put(C_FAVORITE, if (favorite) 1 else 0) }
        writableDatabase.use { db ->
            return db.update(T_DOWNLOADS, cv, "$C_ID = ?", arrayOf(id.toString()))
        }
    }

    /** DELETE 1 dong */
    fun deleteDownload(id: Long): Int {
        writableDatabase.use { db ->
            return db.delete(T_DOWNLOADS, "$C_ID = ?", arrayOf(id.toString()))
        }
    }

    /** DELETE tat ca (trong transaction cho an toan) */
    fun clearDownloads(): Int {
        writableDatabase.use { db ->
            db.beginTransaction()
            return try {
                val n = db.delete(T_DOWNLOADS, null, null)
                db.setTransactionSuccessful()
                n
            } finally {
                db.endTransaction()
            }
        }
    }

    /** COUNT - so video da tai */
    fun countDownloads(): Int {
        readableDatabase.use { db ->
            db.rawQuery("SELECT COUNT(*) FROM $T_DOWNLOADS", null).use { c ->
                return if (c.moveToFirst()) c.getInt(0) else 0
            }
        }
    }

    /** Cap nhat duong dan phu de va trang thai xu ly sau Phase B. */
    fun updateSubtitle(id: Long, subtitlePath: String, status: String, language: String) {
        val cv = ContentValues().apply {
            put(C_SUBTITLE_PATH, subtitlePath)
            put(C_PROCESS_STATUS, status)
            put(C_LANGUAGE, language)
        }
        writableDatabase.use { db ->
            db.update(T_DOWNLOADS, cv, "$C_ID = ?", arrayOf(id.toString()))
        }
    }

    private fun Cursor.getStringOrEmpty(column: String): String =
        runCatching { getString(getColumnIndexOrThrow(column)) }.getOrDefault("") ?: ""
}
