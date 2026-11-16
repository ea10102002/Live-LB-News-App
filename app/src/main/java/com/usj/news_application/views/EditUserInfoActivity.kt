package com.usj.news_application.views

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.usj.news_application.DatabaseHelper
import com.usj.news_application.R
import java.util.Calendar
import java.util.regex.Pattern

class EditUserInfoActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_user_info)

        val toolbar = findViewById<Toolbar>(R.id.toolbarEditProfile)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        databaseHelper = DatabaseHelper(this)

        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etBirthDate = findViewById<EditText>(R.id.etBirthDateEdit)
        val etNewPassword = findViewById<EditText>(R.id.etNewPassword)
        val btnSave = findViewById<Button>(R.id.btnSave)

        etBirthDate.addTextChangedListener(object : TextWatcher {
            private var isEditing = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isEditing) return
                isEditing = true
                val digitsOnly = s.toString().filter { it.isDigit() }.take(8)
                val sb = StringBuilder()
                for (i in digitsOnly.indices) {
                    when (i) {
                        0, 1 -> sb.append(digitsOnly[i])
                        2 -> sb.append("/").append(digitsOnly[i])
                        3 -> sb.append(digitsOnly[i])
                        4 -> sb.append("/").append(digitsOnly[i])
                        5, 6, 7 -> sb.append(digitsOnly[i])
                    }
                }
                etBirthDate.setText(sb.toString())
                etBirthDate.setSelection(sb.length)
                isEditing = false
            }
        })

        val sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
        val loggedInEmail = sharedPreferences.getString("loggedInEmail", null) ?: run {
            Toast.makeText(this, "Error: User not logged in.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val user = databaseHelper.getUserByEmail(loggedInEmail)
        if (user != null) {
            etFirstName.setText(user.firstName)
            etLastName.setText(user.lastName)
            etEmail.setText(user.email)
            etBirthDate.setText(user.birthdate)
        }

        btnSave.setOnClickListener {
            val newFirstName = etFirstName.text.toString().trim()
            val newLastName = etLastName.text.toString().trim()
            val newEmail = etEmail.text.toString().trim()
            val newBirthDate = etBirthDate.text.toString().trim()
            val newPassword = etNewPassword.text.toString().trim()

            if (!Pattern.matches("^[a-zA-Z]+$", newFirstName)) {
                Toast.makeText(this, "First name must contain only letters.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Pattern.matches("^[a-zA-Z]+$", newLastName)) {
                Toast.makeText(this, "Last name must contain only letters.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val emailRegex = "^[a-zA-Z0-9._%+-]+@(gmail|hotmail)\\.com$"
            if (!Pattern.matches(emailRegex, newEmail)) {
                Toast.makeText(this, "Email must end with @gmail.com or @hotmail.com.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!validateBirthDateDdMmYyyy(newBirthDate)) {
                Toast.makeText(this, "Invalid birthdate (use DD/MM/YYYY) or age < 15.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword.isNotEmpty() && newPassword.length < 8) {
                Toast.makeText(this, "Password must be at least 8 characters long.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedPassword = if (newPassword.isNotEmpty()) newPassword else user!!.password

            val isUpdated = databaseHelper.updateUserWithEmail(
                userId = user!!.id,
                firstName = newFirstName,
                lastName = newLastName,
                password = updatedPassword,
                email = newEmail,
                birthdate = newBirthDate
            )

            if (isUpdated) {
                sharedPreferences.edit().putString("loggedInEmail", newEmail).apply()
                Toast.makeText(this, "User information updated successfully!", Toast.LENGTH_SHORT).show()
                val resultIntent = Intent()
                resultIntent.putExtra("updatedEmail", newEmail)
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Failed to update user information.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateBirthDateDdMmYyyy(birthdate: String): Boolean {
        val regex = "\\d{2}/\\d{2}/\\d{4}"
        if (!Pattern.matches(regex, birthdate)) return false
        val parts = birthdate.split("/")
        val day = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val year = parts[2].toIntOrNull() ?: return false
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return year in 1925..(currentYear - 15) && month in 1..12 && day in 1..31
    }
}
