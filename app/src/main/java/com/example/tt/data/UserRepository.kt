package com.example.tt.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.security.MessageDigest

/**
 * User & Session tren SQLite:
 *  - cache profile nguoi dung tu Firebase (hien thi nhanh khi offline)
 *  - dang ky/dang nhap OFFLINE du phong khi Firebase chua cau hinh
 *  - luu phien dang nhap hien tai
 */
class UserRepository(private val db: DatabaseHelper) {

    data class User(
        val uid: String,
        val email: String,
        val name: String,
        val avatar: String,
        val offline: Boolean
    )

    // ---------- HASH mat khau (SHA-256 + salt email) ----------
    private fun hash(password: String, email: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$email:$password".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ---------- DANG KY OFFLINE (du phong) ----------
    fun registerOffline(email: String, password: String, name: String): Result<User> {
        if (email.isBlank() || password.length < 6) {
            return Result.failure(IllegalArgumentException("Email/mat khau khong hop le (mat khau >= 6 ky tu)"))
        }
        val cv = ContentValues().apply {
            put(DatabaseHelper.C_UID, "offline-${'$'}{System.currentTimeMillis()}")
            put(DatabaseHelper.C_EMAIL, email.lowercase().trim())
            put(DatabaseHelper.C_NAME, name)
            put(DatabaseHelper.C_PASSWORD_HASH, hash(password, email))
            put(DatabaseHelper.C_IS_OFFLINE, 1)
            put(DatabaseHelper.C_LAST_LOGIN, System.currentTimeMillis())
        }
        return try {
            db.writableDatabase.use { sqlite ->
                val row = sqlite.insertWithOnConflict(
                    DatabaseHelper.T_USERS, null, cv, SQLiteDatabase.CONFLICT_ABORT
                )
                if (row == -1L) Result.failure(IllegalStateException("Email da ton tai"))
                else Result.success(
                    User(cv.getAsString(DatabaseHelper.C_UID), email, name, "", true)
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------- DANG NHAP OFFLINE ----------
    fun loginOffline(email: String, password: String): Result<User> {
        val e = email.lowercase().trim()
        db.readableDatabase.use { sqlite ->
            sqlite.query(
                DatabaseHelper.T_USERS, null,
                "${'$'}{DatabaseHelper.C_EMAIL} = ? AND ${'$'}{DatabaseHelper.C_IS_OFFLINE} = 1",
                arrayOf(e), null, null, null
            ).use { c ->
                if (!c.moveToFirst()) return Result.failure(IllegalStateException("Tai khoan khong ton tai"))
                val stored = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.C_PASSWORD_HASH))
                if (stored != hash(password, email)) {
                    return Result.failure(IllegalStateException("Sai mat khau"))
                }
                return Result.success(
                    User(
                        uid = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.C_UID)),
                        email = e,
                        name = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.C_NAME)) ?: "",
                        avatar = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.C_AVATAR)) ?: "",
                        offline = true
                    )
                )
            }
        }
    }

    // ---------- CACHE PROFILE FIREBASE ----------
    fun cacheProfile(uid: String, email: String, name: String, avatar: String) {
        val cv = ContentValues().apply {
            put(DatabaseHelper.C_UID, uid)
            put(DatabaseHelper.C_EMAIL, email)
            put(DatabaseHelper.C_NAME, name)
            put(DatabaseHelper.C_AVATAR, avatar)
            put(DatabaseHelper.C_IS_OFFLINE, 0)
            put(DatabaseHelper.C_LAST_LOGIN, System.currentTimeMillis())
        }
        db.writableDatabase.use {
            it.insertWithOnConflict(DatabaseHelper.T_USERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** Lay profile da cache theo uid (nhanh, khong can mang) */
    fun getCachedProfile(uid: String): User? {
        db.readableDatabase.use { sqlite ->
            sqlite.query(
                DatabaseHelper.T_USERS, null,
                "${'$'}{DatabaseHelper.C_UID} = ?", arrayOf(uid), null, null, null
            ).use { c ->
                if (!c.moveToFirst()) return null
                return User(
                    uid, c.getString(c.getColumnIndexOrThrow(DatabaseHelper.C_EMAIL)) ?: "",
                    c.getString(c.getColumnIndexOrThrow(DatabaseHelper.C_NAME)) ?: "",
                    c.getString(c.getColumnIndexOrThrow(DatabaseHelper.C_AVATAR)) ?: "",
                    c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.C_IS_OFFLINE)) == 1
                )
            }
        }
    }

    // ---------- SESSION (phien dang nhap) ----------
    fun saveSession(uid: String) {
        val cv = ContentValues().apply {
            put(DatabaseHelper.C_SESSION_UID, uid)
            put(DatabaseHelper.C_LOGGED_IN, 1)
        }
        db.writableDatabase.use {
            it.insertWithOnConflict(DatabaseHelper.T_SESSIONS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** uid cua phien dang dang nhap, null neu chua co */
    fun currentSessionUid(): String? {
        db.readableDatabase.use { sqlite ->
            sqlite.query(
                DatabaseHelper.T_SESSIONS, null,
                "${'$'}{DatabaseHelper.C_LOGGED_IN} = 1", null, null, null, null
            ).use { c ->
                if (!c.moveToFirst()) return null
                return c.getString(c.getColumnIndexOrThrow(DatabaseHelper.C_SESSION_UID))
            }
        }
    }

    fun clearSession() {
        db.writableDatabase.use {
            it.delete(DatabaseHelper.T_SESSIONS, null, null)
        }
    }
}
