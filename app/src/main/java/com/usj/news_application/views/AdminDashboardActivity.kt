package com.usj.news_application.views

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.usj.news_application.DatabaseHelper
import com.usj.news_application.R
import com.usj.news_application.adapters.UserAdapter
import com.usj.news_application.models.User
import com.usj.news_application.workers.NewsNotificationWorker
import java.util.Calendar
import java.util.regex.Pattern

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var recyclerViewUsers: RecyclerView
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var adapter: UserAdapter
    private lateinit var tvStatTotalUsers: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        databaseHelper = DatabaseHelper(this)

        recyclerViewUsers = findViewById(R.id.recyclerViewUsers)
        tvStatTotalUsers = findViewById(R.id.tvStatTotalUsers)

        val btnOpenFeed = findViewById<Button>(R.id.btnAdminOpenFeed)
        val btnLogout = findViewById<Button>(R.id.btnAdminLogout)
        val btnBroadcastAlert = findViewById<Button>(R.id.btnAdminBroadcastAlert)
        val btnClearForm = findViewById<Button>(R.id.btnClearForm)

        val btnAddUser = findViewById<Button>(R.id.btnAddUser)
        val btnUpdateUser = findViewById<Button>(R.id.btnUpdateUser)
        val btnDeleteUser = findViewById<Button>(R.id.btnDeleteUser)

        val etUserId = findViewById<EditText>(R.id.etUserId)
        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val cbShowPassword = findViewById<CheckBox>(R.id.cbShowPassword)
        val etBirthDate = findViewById<EditText>(R.id.etBirthDate)

        btnOpenFeed.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        btnLogout.setOnClickListener {
            getSharedPreferences("UserSession", MODE_PRIVATE).edit().clear().apply()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        btnBroadcastAlert.setOnClickListener {
            NewsNotificationWorker.triggerImmediateCheck(this, forceDemoAlert = true)
            Toast.makeText(
                this,
                "🔔 Breaking News / Strike Alert broadcasted via WorkManager!",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnClearForm.setOnClickListener {
            clearInputs()
        }

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

        cbShowPassword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etPassword.setSelection(etPassword.text.length)
        }

        recyclerViewUsers.layoutManager = LinearLayoutManager(this)
        adapter = UserAdapter(
            users = databaseHelper.getAllUsers(),
            onSelectUser = { user ->
                populateFormWithUser(user)
                Toast.makeText(this, "Loaded ${user.firstName} (#${user.id}) for editing", Toast.LENGTH_SHORT).show()
            },
            onDeleteUser = { user ->
                confirmAndDeleteUser(user)
            }
        )
        recyclerViewUsers.adapter = adapter
        refreshUserList()

        btnAddUser.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val birthDate = etBirthDate.text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty() || birthDate.isEmpty()) {
                Toast.makeText(this, "All fields are required to add a user.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!validateBirthDateDdMmYyyy(birthDate)) {
                Toast.makeText(this, "Invalid Birth Date! Use DD/MM/YYYY (e.g. 15/05/2002).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (databaseHelper.insertUser(firstName, lastName, email, password, birthDate)) {
                Toast.makeText(this, "User added successfully!", Toast.LENGTH_SHORT).show()
                refreshUserList()
                clearInputs()
            } else {
                Toast.makeText(this, "Failed to add user. Email might already exist.", Toast.LENGTH_SHORT).show()
            }
        }

        btnUpdateUser.setOnClickListener {
            val userId = etUserId.text.toString().toIntOrNull()
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val birthDate = etBirthDate.text.toString().trim()

            if (userId == null) {
                Toast.makeText(this, "Tap '✏️ Edit' on any user card above first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = databaseHelper.getUserById(userId)
            if (user == null) {
                Toast.makeText(this, "User not found.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (birthDate.isNotEmpty() && !validateBirthDateDdMmYyyy(birthDate)) {
                Toast.makeText(this, "Invalid Birth Date! Use DD/MM/YYYY (e.g. 15/05/2002).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedFirstName = if (firstName.isNotEmpty()) firstName else user.firstName
            val updatedLastName = if (lastName.isNotEmpty()) lastName else user.lastName
            val updatedEmail = if (email.isNotEmpty()) email else user.email
            val updatedPassword = if (password.isNotEmpty()) password else user.password
            val updatedBirthDate = if (birthDate.isNotEmpty()) birthDate else user.birthdate

            val oldEmail = user.email
            val success = databaseHelper.updateUserWithEmail(
                userId = userId,
                firstName = updatedFirstName,
                lastName = updatedLastName,
                password = updatedPassword,
                email = updatedEmail,
                birthdate = updatedBirthDate
            )

            if (success) {
                val prefs = getSharedPreferences("UserSession", MODE_PRIVATE)
                if (prefs.getString("loggedInEmail", null) == oldEmail) {
                    prefs.edit().putString("loggedInEmail", updatedEmail).apply()
                }
                Toast.makeText(this, "User #${userId} updated (DOB: $updatedBirthDate)!", Toast.LENGTH_SHORT).show()
                refreshUserList()
                clearInputs()
            } else {
                Toast.makeText(this, "Failed to update user.", Toast.LENGTH_SHORT).show()
            }
        }

        btnDeleteUser.setOnClickListener {
            val userId = etUserId.text.toString().toIntOrNull()
            if (userId == null) {
                Toast.makeText(this, "Tap a user card above or enter User ID to delete.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val user = databaseHelper.getUserById(userId)
            if (user != null) {
                confirmAndDeleteUser(user)
            } else {
                Toast.makeText(this, "User ID #$userId not found.", Toast.LENGTH_SHORT).show()
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
        return year in 1925..(currentYear - 10) && month in 1..12 && day in 1..31
    }

    private fun populateFormWithUser(user: User) {
        findViewById<EditText>(R.id.etUserId).setText(user.id.toString())
        findViewById<EditText>(R.id.etFirstName).setText(user.firstName)
        findViewById<EditText>(R.id.etLastName).setText(user.lastName)
        findViewById<EditText>(R.id.etEmail).setText(user.email)
        findViewById<EditText>(R.id.etPassword).setText(user.password)
        findViewById<EditText>(R.id.etBirthDate).setText(user.birthdate)
    }

    private fun confirmAndDeleteUser(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Delete User #${user.id}?")
            .setMessage("Are you sure you want to delete ${user.firstName} ${user.lastName} (${user.email})?")
            .setPositiveButton("Delete") { _, _ ->
                if (databaseHelper.deleteUser(user.id)) {
                    Toast.makeText(this, "Deleted ${user.firstName} ${user.lastName}", Toast.LENGTH_SHORT).show()
                    refreshUserList()
                    clearInputs()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshUserList() {
        val users = databaseHelper.getAllUsers()
        tvStatTotalUsers.text = users.size.toString()
        adapter.updateUsers(users)
    }

    private fun clearInputs() {
        findViewById<EditText>(R.id.etUserId).text.clear()
        findViewById<EditText>(R.id.etFirstName).text.clear()
        findViewById<EditText>(R.id.etLastName).text.clear()
        findViewById<EditText>(R.id.etEmail).text.clear()
        findViewById<EditText>(R.id.etPassword).text.clear()
        findViewById<EditText>(R.id.etBirthDate).text.clear()
    }
}
