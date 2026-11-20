package com.example.tt

import android.app.Application
import com.example.tt.network.dtk.DtkConfig

/**
 * Application class: khoi tao Firebase tu dong qua google-services.json
 * (FirebaseApp.initializeApp() duoc goi ngam khi process khoi dong).
 */
class TTApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Cấu hình API DTK lưu trên máy; mặc định rỗng → dùng nguồn cũ.
        DtkConfig.init(this)
    }
}
