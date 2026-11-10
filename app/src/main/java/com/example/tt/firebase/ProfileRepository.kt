package com.example.tt.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * KY THUAT 3 - FIREBASE FIRESTORE (CSDL backend)
 * Collection "users" - moi user 1 document:
 *   users/{uid} = { name, email, avatar, downloadCount, createdAt }
 *
 * App goi toi backend nay de: doc profile, cap nhat profile,
 * va tang bo dem so video da tai moi khi tai xong 1 video.
 */
class ProfileRepository {

    private val db: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        null
    }

    data class Profile(
        val name: String = "",
        val email: String = "",
        val avatar: String = "",
        val downloadCount: Long = 0
    )

    /** DOC profile tu Firestore */
    suspend fun getProfile(uid: String): Result<Profile> {
        val d = db ?: return Result.failure(IllegalStateException("Firestore chua cau hinh"))
        return try {
            val doc = d.collection("users").document(uid).get().await()
            if (!doc.exists()) return Result.success(Profile()) // chua co, tra rong
            Result.success(
                Profile(
                    name = doc.getString("name") ?: "",
                    email = doc.getString("email") ?: "",
                    avatar = doc.getString("avatar") ?: "",
                    downloadCount = doc.getLong("downloadCount") ?: 0
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** TAO/CAP NHAT profile (upsert) */
    suspend fun saveProfile(
        uid: String, name: String, email: String, avatar: String
    ): Result<Unit> {
        val d = db ?: return Result.failure(IllegalStateException("Firestore chua cau hinh"))
        return try {
            d.collection("users").document(uid).set(
                mapOf(
                    "name" to name,
                    "email" to email,
                    "avatar" to avatar,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** TANG bo dem video da tai (goi moi khi tai xong) - dung transaction nguyen tu */
    suspend fun incrementDownloadCount(uid: String): Result<Long> {
        val d = db ?: return Result.failure(IllegalStateException("Firestore chua cau hinh"))
        return try {
            var newValue = 0L
            d.runTransaction { tx ->
                val ref = d.collection("users").document(uid)
                val cur = tx.get(ref).getLong("downloadCount") ?: 0L
                newValue = cur + 1
                tx.update(ref, "downloadCount", newValue)
                null
            }.await()
            Result.success(newValue)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
