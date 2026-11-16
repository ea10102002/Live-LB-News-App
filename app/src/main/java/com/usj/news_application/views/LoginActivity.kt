package com.usj.news_application.views

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.usj.news_application.DatabaseHelper
import com.usj.news_application.R
import com.usj.news_application.viewmodels.LoginViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmailLogin)
        val etPassword = findViewById<EditText>(R.id.etPasswordLogin)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoToSignUp = findViewById<Button>(R.id.btnGoToSignUp)

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            val isAdminLogin = (email.equals("admin", ignoreCase = true) ||
                    email.equals("admin@gmail.com", ignoreCase = true) ||
                    email.equals("admin@livelb.com", ignoreCase = true)) &&
                    (password == "admin" || password == "admin123")

            if (isAdminLogin) {
                val dbHelper = DatabaseHelper(this)
                val adminEmail = "admin@gmail.com"
                if (dbHelper.getUserByEmail(adminEmail) == null) {
                    dbHelper.insertUser("Admin", "LiveLB", adminEmail, "admin123", "01/01/1995")
                }

                val sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
                sharedPreferences.edit()
                    .putString("loggedInEmail", adminEmail)
                    .putBoolean("isAdmin", true)
                    .apply()

                Toast.makeText(this, "Welcome, Administrator! 🛡️", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, AdminDashboardActivity::class.java))
                finish()
                return@setOnClickListener
            }

            if (viewModel.validateUser(this, email, password)) {
                val sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
                sharedPreferences.edit()
                    .putString("loggedInEmail", email)
                    .putBoolean("isAdmin", false)
                    .apply()

                Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Invalid email or password.", Toast.LENGTH_SHORT).show()
            }
        }

        btnGoToSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }
}
