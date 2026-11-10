package com.example.tt

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tt.data.DatabaseHelper
import com.example.tt.data.UserRepository
import com.example.tt.databinding.ActivityLoginBinding
import com.example.tt.firebase.AuthRepository
import kotlinx.coroutines.launch

/**
 * Man Dang nhap / Dang ky (Firebase Auth, co du phong offline SQLite)
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = AuthRepository(UserRepository(DatabaseHelper(this)))

        // Da dang nhap tu phien truoc -> vao thang app
        if (auth.currentUser() != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Chuyen tab Dang nhap <-> Dang ky
        binding.tvSwitchMode.setOnClickListener { switchMode() }
        binding.btnAction.setOnClickListener {
            if (binding.tvSwitchMode.text.contains("Đăng ký")) doLogin() else doRegister()
        }
    }

    private fun switchMode() {
        val isLoginMode = binding.btnAction.text.contains("Đăng nhập")
        if (isLoginMode) { // sang dang ky
            binding.tvTitle.text = "Tạo tài khoản"
            binding.btnAction.text = "Đăng ký"
            binding.tvSwitchMode.text = "Đã có tài khoản? Đăng nhập"
            binding.tilName.visibility = View.VISIBLE
        } else {
            binding.tvTitle.text = "Đăng nhập"
            binding.btnAction.text = "Đăng nhập"
            binding.tvSwitchMode.text = "Chưa có tài khoản? Đăng ký"
            binding.tilName.visibility = View.GONE
        }
    }

    private fun doLogin() {
        val email = binding.etEmail.text.toString().trim()
        val pass = binding.etPass.text.toString()
        if (email.isEmpty() || pass.isEmpty()) {
            toast("Nhập đủ email và mật khẩu"); return
        }
        setLoading(true)
        lifecycleScope.launch {
            auth.login(email, pass)
                .onSuccess {
                    toast("Xin chào ${'$'}{it.name}!")
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
                .onFailure { toast(it.message ?: "Đăng nhập thất bại") }
            setLoading(false)
        }
    }

    private fun doRegister() {
        val email = binding.etEmail.text.toString().trim()
        val pass = binding.etPass.text.toString()
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty() || email.isEmpty() || pass.length < 6) {
            toast("Nhập đủ thông tin (mật khẩu ≥ 6 ký tự)"); return
        }
        setLoading(true)
        lifecycleScope.launch {
            auth.register(email, pass, name)
                .onSuccess {
                    toast("Tạo tài khoản thành công!")
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
                .onFailure { toast(it.message ?: "Đăng ký thất bại") }
            setLoading(false)
        }
    }

    private fun setLoading(on: Boolean) {
        binding.progress.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnAction.isEnabled = !on
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
