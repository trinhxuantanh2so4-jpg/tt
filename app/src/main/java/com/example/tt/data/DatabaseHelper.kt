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
        private const val DATABASE_VERSION = 3

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
        // Phien ban 3: thuyet minh + tuy chon xu ly
        const val C_DUB_PATH = "dub_path"
        const val C_VOICE = "voice"
        const val C_SUBTITLE_LANG = "subtitle_lang"
        const val C_BURN_PATH = "burn_path"

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
                $C_PROCESS_STATUS TEXT DEFAULT '',
                $C_DUB_PATH TEXT DEFAULT '',
                $C_VOICE TEXT DEFAULT '',
                $C_SUBTITLE_LANG TEXT DEFAULT '',
                $C_BURN_PATH TEXT DEFAULT ''
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
        if (oldVersion < 3) {
            // Phase D: track thuyet minh + lua chon giong / ngon ngu
            addColumnIfMissing(db, C_DUB_PATH, "TEXT DEFAULT ''")
            addColumnIfMissing(db, C_VOICE, "TEXT DEFAULT ''")
            addColumnIfMissing(db, C_SUBTITLE_LANG, "TEXT DEFAULT ''")
            addColumnIfMissing(db, C_BURN_PATH, "TEXT DEFAULT ''")
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

    /**
     * INSERT - tai lai cung video thi cap nhat dong cu thay vi REPLACE,
     * giu nguyen id + du lieu da xu ly (vietsub / thuyet minh) de khong lam
     * mo coi file .srt / .mp4 trong filesDir.
     */
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
            put(C_CREATED_AT, rec.createdAt)
            put(C_PLATFORM, rec.platform.id)
            put(C_FAVORITE, rec.favorite)
            put(C_LANGUAGE, rec.language)
            put(C_SUBTITLE_PATH, rec.subtitlePath)
            put(C_PROCESS_STATUS, rec.processStatus)
            put(C_DUB_PATH, rec.dubPath)
            put(C_VOICE, rec.voice)
            put(C_SUBTITLE_LANG, rec.subtitleLang)
            put(C_BURN_PATH, rec.burnPath)
        }
        writableDatabase.use { db ->
            var existingId = -1L
            var favorite = 0
            var subtitlePath = ""
            var processStatus = ""
            var dubPath = ""
            var voice = ""
            var subtitleLang = ""
            var burnPath = ""
            var language = ""

            db.query(
                T_DOWNLOADS,
                arrayOf(
                    C_ID, C_FAVORITE, C_SUBTITLE_PATH, C_PROCESS_STATUS,
                    C_DUB_PATH, C_VOICE, C_SUBTITLE_LANG, C_BURN_PATH, C_LANGUAGE
                ),
                "$C_VIDEO_ID = ?",
                arrayOf(rec.videoId),
                null, null, null
            ).use { c ->
                if (c.moveToFirst()) {
                    existingId = c.getLong(0)
                    favorite = c.getInt(1)
                    subtitlePath = c.getString(2) ?: ""
                    processStatus = c.getString(3) ?: ""
                    dubPath = c.getString(4) ?: ""
                    voice = c.getString(5) ?: ""
                    subtitleLang = c.getString(6) ?: ""
                    burnPath = c.getString(7) ?: ""
                    language = c.getString(8) ?: ""
                }
            }

            if (existingId > 0) {
                // Giu du lieu xu ly cu; chi cap nhat khi ban ghi moi co gia tri.
                cv.put(C_FAVORITE, if (rec.favorite != 0) rec.favorite else favorite)
                cv.put(C_SUBTITLE_PATH, rec.subtitlePath.ifEmpty { subtitlePath })
                cv.put(C_PROCESS_STATUS, rec.processStatus.ifEmpty { processStatus })
                cv.put(C_DUB_PATH, rec.dubPath.ifEmpty { dubPath })
                cv.put(C_VOICE, rec.voice.ifEmpty { voice })
                cv.put(C_SUBTITLE_LANG, rec.subtitleLang.ifEmpty { subtitleLang })
                cv.put(C_BURN_PATH, rec.burnPath.ifEmpty { burnPath })
                cv.put(C_LANGUAGE, rec.language.ifEmpty { language })
                db.update(T_DOWNLOADS, cv, "$C_ID = ?", arrayOf(existingId.toString()))
                return existingId
            }

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

    private fun queryDownloads(where: String?, args: Array<String>? = null): List<DownloadRecord> {
        val list = mutableListOf<DownloadRecord>()
        readableDatabase.use { db ->
            db.query(
                T_DOWNLOADS, null, where, args, null, null,
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
                            processStatus = c.getStringOrEmpty(C_PROCESS_STATUS),
                            dubPath = c.getStringOrEmpty(C_DUB_PATH),
                            voice = c.getStringOrEmpty(C_VOICE),
                            subtitleLang = c.getStringOrEmpty(C_SUBTITLE_LANG),
                            burnPath = c.getStringOrEmpty(C_BURN_PATH)
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
    fun updateSubtitle(id: Long, subtitlePath: String, status: String, language: String) =
        updateSubtitle(id, subtitlePath, status, language, language)

    /**
     * Luu day du thong tin phu de. them [sourceLanguage] de biet video goc la tieng Trung/Anh/Viet;
     * khong can doi schema vi C_SUBTITLE_LANG da co tu version 3.
     */
    fun updateSubtitle(
        id: Long,
        subtitlePath: String,
        status: String,
        language: String,
        sourceLanguage: String
    ) {
        val cv = ContentValues().apply {
            put(C_SUBTITLE_PATH, subtitlePath)
            put(C_PROCESS_STATUS, status)
            put(C_LANGUAGE, language)
            put(C_SUBTITLE_LANG, sourceLanguage)
        }
        writableDatabase.use { db ->
            db.update(T_DOWNLOADS, cv, "$C_ID = ?", arrayOf(id.toString()))
        }
    }

    /** Xoa sach ban xu ly, giu nguyen video goc va cac thong tin tai. */
    fun clearProcessed(id: Long) {
        val cv = ContentValues().apply {
            put(C_SUBTITLE_PATH, "")
            put(C_PROCESS_STATUS, "")
            put(C_LANGUAGE, "")
            put(C_SUBTITLE_LANG, "")
            put(C_DUB_PATH, "")
            put(C_VOICE, "")
            put(C_BURN_PATH, "")
        }
        writableDatabase.use { db ->
            db.update(T_DOWNLOADS, cv, "$C_ID = ?", arrayOf(id.toString()))
        }
    }

    /** Phase D: luu ban MP4 thuyet minh + giong da dung. */
    fun updateDub(id: Long, dubPath: String, voice: String, status: String) {
        val cv = ContentValues().apply {
            put(C_DUB_PATH, dubPath)
            put(C_VOICE, voice)
            put(C_PROCESS_STATUS, status)
        }
        writableDatabase.use { db ->
            db.update(T_DOWNLOADS, cv, "$C_ID = ?", arrayOf(id.toString()))
        }
    }

    private fun Cursor.getStringOrEmpty(column: String): String =
        runCatching { getString(getColumnIndexOrThrow(column)) }.getOrDefault("") ?: ""
}
