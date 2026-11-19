package com.example.tt.ui

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.tt.DouyinActivity
import com.example.tt.HistoryActivity
import com.example.tt.MainActivity
import com.example.tt.ProfileActivity
import com.example.tt.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Bottom navigation dung chung cho 4 man hinh chinh.
 * Khong dung Fragment de giu nguyen logic cua tung Activity (an toan hon khi nang cap UI).
 */
@SuppressLint("NonConstantResourceId")
fun AppCompatActivity.setupBottomNav(nav: BottomNavigationView, currentId: Int) {
    nav.selectedItemId = currentId
    nav.setOnItemSelectedListener { item ->
        if (item.itemId == currentId) return@setOnItemSelectedListener true

        val target = when (item.itemId) {
            R.id.nav_download -> MainActivity::class.java
            R.id.nav_douyin -> DouyinActivity::class.java
            R.id.nav_history -> HistoryActivity::class.java
            R.id.nav_profile -> ProfileActivity::class.java
            else -> return@setOnItemSelectedListener false
        }

        startActivity(
            Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        true
    }
}
