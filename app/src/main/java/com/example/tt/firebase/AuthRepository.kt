package com.example.tt.firebase

import android.util.Log
import com.example.tt.data.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.tasks.await

/**
 * KY THUAT 3 - FIREBASE AUTH (backend do minh tao tren Firebase Console)
 * Dang ky / Dang nhap / Dang xuat / Lay user hien tai.
 *
 * Chien luoc du phong: neu Firebase chua cau hinh that (google-services.json
 * dang la ban demo) thi tu chuyen sang che do OFFLINE bang SQLite de app
 * van chay duoc ngay khi demo.
 */
class AuthRepository(private val localUsers: UserRepository) {

    private val auth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        Log.w("AuthRepository", "Firebase chua cau hinh: ${'$'}{e.message}")
        null
    }

    val isFirebaseReady: Boolean get() = auth != null

    // ---------- DANG KY ----------
    suspend fun register(email: String, password: String, name: String): Result<UserRepository.User> {
        val a = auth
            ?: return localUsers.registerOffline(email, password, name) // du phong
        return try {
            val result = a.createUserWithEmailAndPassword(email, password).await()
            // Cap nhat displayName ngay tren backend Firebase
            result.user?.updateProfile(
                userProfileChangeRequest { displayName = name }
            )?.await()
            val u = result.user!!
            localUsers.cacheProfile(u.uid, email, name, "")
            localUsers.saveSession(u.uid)
            Result.success(UserRepository.User(u.uid, email, name, "", false))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------- DANG NHAP ----------
    suspend fun login(email: String, password: String): Result<UserRepository.User> {
        val a = auth
            ?: return localUsers.loginOffline(email, password) // du phong
        return try {
            val result = a.signInWithEmailAndPassword(email, password).await()
            val u = result.user!!
            val cached = localUsers.getCachedProfile(u.uid)
            val name = u.displayName ?: cached?.name ?: email.substringBefore("@")
            localUsers.cacheProfile(u.uid, email, name, "")
            localUsers.saveSession(u.uid)
            Result.success(UserRepository.User(u.uid, email, name, "", false))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------- DANG XUAT ----------
    fun logout() {
        auth?.signOut()
        localUsers.clearSession()
    }

    // ---------- USER HIEN TAI (tu SQLite session cho nhanh) ----------
    fun currentUser(): UserRepository.User? {
        val uid = localUsers.currentSessionUid() ?: return null
        return localUsers.getCachedProfile(uid)
    }
}
