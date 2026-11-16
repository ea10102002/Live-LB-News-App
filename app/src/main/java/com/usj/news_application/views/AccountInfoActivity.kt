package com.usj.news_application.views

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.usj.news_application.DatabaseHelper
import com.usj.news_application.R
import com.usj.news_application.workers.NewsNotificationWorker

class AccountInfoActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_info)

        val toolbar = findViewById<Toolbar>(R.id.toolbarAccount)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        databaseHelper = DatabaseHelper(this)

        val btnEditInfo = findViewById<Button>(R.id.btnEditInfo)
        val btnTestNotification = findViewById<Button>(R.id.btnTestNotification)
        val btnAdminDashboard = findViewById<Button>(R.id.btnAdminDashboard)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        populateUserInfo()

        btnEditInfo.setOnClickListener {
            startActivity(Intent(this, EditUserInfoActivity::class.java))
        }

        btnTestNotification.setOnClickListener {
            NewsNotificationWorker.triggerImmediateCheck(this, forceDemoAlert = true)
            Toast.makeText(
                this,
                "Background Worker triggered! Check your top notification bar 🔔",
                Toast.LENGTH_SHORT
            ).show()
        }

        val sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
        val isAdmin = sharedPreferences.getBoolean("isAdmin", false)
        btnAdminDashboard.visibility = if (isAdmin) View.VISIBLE else View.GONE
        btnAdminDashboard.setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
        }

        btnLogout.setOnClickListener {
            sharedPreferences.edit().clear().apply()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        populateUserInfo()
    }

    private fun populateUserInfo() {
        val tvHeader = findViewById<TextView>(R.id.tvHeader)
        val tvFirstName = findViewById<TextView>(R.id.tvFirstName)
        val tvLastName = findViewById<TextView>(R.id.tvLastName)
        val tvEmail = findViewById<TextView>(R.id.tvEmail)
        val tvBirthDate = findViewById<TextView>(R.id.tvBirthDate)
        val tvSavedCount = findViewById<TextView>(R.id.tvSavedCount)

        val sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
        val loggedInEmail = sharedPreferences.getString("loggedInEmail", null)

        if (loggedInEmail != null) {
            val user = databaseHelper.getUserByEmail(loggedInEmail)
            if (user != null) {
                tvHeader.text = "${user.firstName} ${user.lastName}"
                tvFirstName.text = "First Name: ${user.firstName}"
                tvLastName.text = "Last Name: ${user.lastName}"
                tvEmail.text = "Email: ${user.email}"
                tvBirthDate.text = "Birthdate: ${user.birthdate}"
                val savedCount = databaseHelper.getSavedArticlesForUser(user.email).size
                tvSavedCount.text = "🔖 Saved Articles in SQLite: $savedCount"
            } else {
                Toast.makeText(this, "User not found.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No user logged in.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
