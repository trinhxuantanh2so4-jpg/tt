package com.example.tt

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tt.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ví dụ: đếm số lần bấm nút
        var count = 0
        binding.buttonHello.setOnClickListener {
            count++
            binding.textMessage.text = "Bạn đã bấm $count lần! 👋"
        }
    }
}
