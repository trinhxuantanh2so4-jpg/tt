package com.example.tt

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
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
 * Man Dang nhap / Dang ky (Firebase Auth, co du phong offline SQLite).
 * UI: validate ngay tren o nhap, progress nam trong nut, ghi nho email lan truoc.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: AuthRepository
    private var isRegisterMode = false

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

        // Ghi nho email lan dang nhap truoc
        val prefs = getSharedPreferences("tt_prefs", MODE_PRIVATE)
        val lastEmail = prefs.getString("last_email", null)
        if (!lastEmail.isNullOrEmpty()) binding.etEmail.setText(lastEmail)

        binding.tvSwitchMode.setOnClickListener { switchMode() }
        binding.btnAction.setOnClickListener {
            if (isRegisterMode) doRegister() else doLogin()
        }
    }

    private fun switchMode() {
        isRegisterMode = !isRegisterMode

        if (isRegisterMode) {
            binding.tvTitle.text = getString(R.string.register_title)
            binding.btnAction.text = getString(R.string.action_register)
            binding.tvSwitchMode.text = getString(R.string.switch_to_login)
            binding.tilName.visibility = View.VISIBLE
            binding.tilName.error = null
        } else {
            binding.tvTitle.text = getString(R.string.login_title)
            binding.btnAction.text = getString(R.string.action_login)
            binding.tvSwitchMode.text = getString(R.string.switch_to_register)
            binding.tilName.visibility = View.GONE
        }
        binding.tilEmail.error = null
        binding.tilPass.error = null
    }

    // ---------------- DANG NHAP ----------------
    private fun doLogin() {
        val email = binding.etEmail.text.toString().trim()
        val pass = binding.etPass.text.toString()
        if (!validate(email, pass, requireName = false)) return

        setLoading(true)
        lifecycleScope.launch {
            auth.login(email, pass)
                .onSuccess {
                    rememberEmail(email)
                    toast(getString(R.string.login_welcome, it.name))
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
                .onFailure { toast(it.message ?: getString(R.string.login_title)) }
            setLoading(false)
        }
    }

    // ---------------- DANG KY ----------------
    private fun doRegister() {
        val email = binding.etEmail.text.toString().trim()
        val pass = binding.etPass.text.toString()
        val name = binding.etName.text.toString().trim()
        if (!validate(email, pass, requireName = true, name = name)) return

        setLoading(true)
        lifecycleScope.launch {
            auth.register(email, pass, name)
                .onSuccess {
                    rememberEmail(email)
                    toast(getString(R.string.register_success))
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
                .onFailure { toast(it.message ?: getString(R.string.register_title)) }
            setLoading(false)
        }
    }

    // ---------------- VALIDATE INLINE ----------------
    private fun validate(
        email: String,
        pass: String,
        requireName: Boolean,
        name: String = ""
    ): Boolean {
        var ok = true
        binding.tilEmail.error = null
        binding.tilPass.error = null
        binding.tilName.error = null

        if (requireName && name.isEmpty()) {
            binding.tilName.error = getString(R.string.error_name_empty)
            ok = false
        }
        when {
            email.isEmpty() -> {
                binding.tilEmail.error = getString(R.string.error_email_empty)
                ok = false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.tilEmail.error = getString(R.string.error_email_invalid)
                ok = false
            }
        }
        when {
            pass.isEmpty() -> {
                binding.tilPass.error = getString(R.string.error_password_empty)
                ok = false
            }
            pass.length < 6 -> {
                binding.tilPass.error = getString(R.string.error_password_short)
                ok = false
            }
        }
        return ok
    }

    private fun rememberEmail(email: String) {
        getSharedPreferences("tt_prefs", MODE_PRIVATE)
            .edit()
            .putString("last_email", email)
            .apply()
    }

    private fun setLoading(on: Boolean) {
        binding.progress.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnAction.isEnabled = !on
        binding.btnAction.text = when {
            on -> ""
            isRegisterMode -> getString(R.string.action_register)
            else -> getString(R.string.action_login)
        }
        binding.tvSwitchMode.isEnabled = !on
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
