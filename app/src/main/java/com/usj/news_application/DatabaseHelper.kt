package com.usj.news_application

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.usj.news_application.models.News
import com.usj.news_application.models.User

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "UserDatabase.db"
        private const val DATABASE_VERSION = 3

        const val TABLE_USERS = "users"
        const val COLUMN_ID = "id"
        const val COLUMN_FIRST_NAME = "first_name"
        const val COLUMN_LAST_NAME = "last_name"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_PASSWORD = "password"
        const val COLUMN_BIRTHDATE = "birthdate"

        const val TABLE_SAVED_ARTICLES = "saved_articles"
        const val COLUMN_SAVED_ID = "saved_id"
        const val COLUMN_USER_EMAIL = "user_email"
        const val COLUMN_NEWS_ID = "news_id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_LOCATION = "location"
        const val COLUMN_MAP_LOCATION = "map_location"
        const val COLUMN_DATETIME = "datetime"
        const val COLUMN_DETAILED_DESC = "detailed_description"
        const val COLUMN_IMAGE_URL = "image_url"

        fun normalizeDateToDdMmYyyy(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val clean = raw.trim().replace("-", "/")
            val parts = clean.split("/")
            if (parts.size == 3 && parts[0].length == 4) {
                val yyyy = parts[0]
                val mm = parts[1].padStart(2, '0')
                val dd = parts[2].padStart(2, '0')
                return "$dd/$mm/$yyyy"
            }
            return clean
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTableQuery = """
            CREATE TABLE IF NOT EXISTS $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_FIRST_NAME TEXT,
                $COLUMN_LAST_NAME TEXT,
                $COLUMN_EMAIL TEXT UNIQUE,
                $COLUMN_PASSWORD TEXT,
                $COLUMN_BIRTHDATE TEXT
            )
        """.trimIndent()
        db.execSQL(createUsersTableQuery)

        createSavedArticlesTable(db)
    }

    private fun createSavedArticlesTable(db: SQLiteDatabase) {
        val createSavedTableQuery = """
            CREATE TABLE IF NOT EXISTS $TABLE_SAVED_ARTICLES (
                $COLUMN_SAVED_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USER_EMAIL TEXT,
                $COLUMN_NEWS_ID TEXT,
                $COLUMN_TITLE TEXT,
                $COLUMN_CONTENT TEXT,
                $COLUMN_LOCATION TEXT,
                $COLUMN_MAP_LOCATION TEXT,
                $COLUMN_DATETIME TEXT,
                $COLUMN_DETAILED_DESC TEXT,
                $COLUMN_IMAGE_URL TEXT,
                UNIQUE($COLUMN_USER_EMAIL, $COLUMN_NEWS_ID)
            )
        """.trimIndent()
        db.execSQL(createSavedTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        createSavedArticlesTable(db)
        migrateLegacyBirthdates(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            migrateLegacyBirthdates(db)
        }
    }

    private fun migrateLegacyBirthdates(db: SQLiteDatabase) {
        try {
            val cursor = db.rawQuery("SELECT $COLUMN_ID, $COLUMN_BIRTHDATE FROM $TABLE_USERS", null)
            val updates = mutableListOf<Pair<Int, String>>()
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getInt(0)
                    val oldDob = cursor.getString(1) ?: ""
                    val newDob = normalizeDateToDdMmYyyy(oldDob)
                    if (newDob != oldDob && newDob.isNotEmpty()) {
                        updates.add(id to newDob)
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()

            for ((id, newDob) in updates) {
                val values = ContentValues().apply {
                    put(COLUMN_BIRTHDATE, newDob)
                }
                db.update(TABLE_USERS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
            }
        } catch (_: Exception) {
        }
    }

    fun insertUser(firstName: String, lastName: String, email: String, password: String, birthdate: String): Boolean {
        val db = writableDatabase
        val normalizedDob = normalizeDateToDdMmYyyy(birthdate)
        val values = ContentValues().apply {
            put(COLUMN_FIRST_NAME, firstName)
            put(COLUMN_LAST_NAME, lastName)
            put(COLUMN_EMAIL, email)
            put(COLUMN_PASSWORD, password)
            put(COLUMN_BIRTHDATE, normalizedDob)
        }

        return try {
            db.insertOrThrow(TABLE_USERS, null, values) != -1L
        } catch (e: Exception) {
            false
        }
    }

    fun updateUserWithEmail(
        userId: Int,
        firstName: String,
        lastName: String,
        password: String,
        email: String,
        birthdate: String? = null
    ): Boolean {
        val db = writableDatabase
        val oldUser = getUserById(userId)
        val values = ContentValues().apply {
            put(COLUMN_FIRST_NAME, firstName)
            put(COLUMN_LAST_NAME, lastName)
            put(COLUMN_EMAIL, email)
            put(COLUMN_PASSWORD, password)
            if (!birthdate.isNullOrBlank()) {
                put(COLUMN_BIRTHDATE, normalizeDateToDdMmYyyy(birthdate))
            }
        }

        val updated = try {
            db.update(TABLE_USERS, values, "$COLUMN_ID = ?", arrayOf(userId.toString())) > 0
        } catch (e: Exception) {
            false
        }

        if (updated && oldUser != null && oldUser.email != email) {
            val savedValues = ContentValues().apply {
                put(COLUMN_USER_EMAIL, email)
            }
            db.update(TABLE_SAVED_ARTICLES, savedValues, "$COLUMN_USER_EMAIL = ?", arrayOf(oldUser.email))
        }
        return updated
    }

    fun deleteUser(userId: Int): Boolean {
        val db = writableDatabase
        val user = getUserById(userId)
        val deleted = db.delete(TABLE_USERS, "$COLUMN_ID = ?", arrayOf(userId.toString())) > 0
        if (deleted && user != null) {
            db.delete(TABLE_SAVED_ARTICLES, "$COLUMN_USER_EMAIL = ?", arrayOf(user.email))
        }
        return deleted
    }

    fun validateUser(email: String, password: String): Boolean {
        val db = readableDatabase
        val query = """
            SELECT * FROM $TABLE_USERS WHERE $COLUMN_EMAIL = ? AND $COLUMN_PASSWORD = ?
        """
        val cursor = db.rawQuery(query, arrayOf(email, password))
        val isValid = cursor.count > 0
        cursor.close()
        return isValid
    }

    fun getUserById(userId: Int): User? {
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_USERS WHERE $COLUMN_ID = ?"
        val cursor = db.rawQuery(query, arrayOf(userId.toString()))

        if (cursor.moveToFirst()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
            val firstName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FIRST_NAME))
            val lastName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_NAME))
            val email = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL))
            val password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD))
            val birthdate = normalizeDateToDdMmYyyy(
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BIRTHDATE))
            )

            cursor.close()
            return User(id, firstName, lastName, email, birthdate, password)
        }

        cursor.close()
        return null
    }

    fun getAllUsers(): List<User> {
        val userList = mutableListOf<User>()
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_USERS"
        val cursor = db.rawQuery(query, null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val firstName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FIRST_NAME))
                val lastName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_NAME))
                val email = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL))
                val password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD))
                val birthdate = normalizeDateToDdMmYyyy(
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BIRTHDATE))
                )

                val user = User(id, firstName, lastName, email, birthdate, password)
                userList.add(user)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return userList
    }

    fun getUserByEmail(email: String): User? {
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_USERS WHERE $COLUMN_EMAIL = ?"
        val cursor = db.rawQuery(query, arrayOf(email))

        if (cursor.moveToFirst()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
            val firstName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FIRST_NAME))
            val lastName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_NAME))
            val password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD))
            val birthdate = normalizeDateToDdMmYyyy(
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BIRTHDATE))
            )

            cursor.close()
            return User(id, firstName, lastName, email, birthdate, password)
        }

        cursor.close()
        return null
    }

    fun saveArticleForUser(userEmail: String, news: News): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USER_EMAIL, userEmail)
            put(COLUMN_NEWS_ID, news.id)
            put(COLUMN_TITLE, news.title)
            put(COLUMN_CONTENT, news.content)
            put(COLUMN_LOCATION, news.location)
            put(COLUMN_MAP_LOCATION, news.mapLocation)
            put(COLUMN_DATETIME, news.datetime)
            put(COLUMN_DETAILED_DESC, news.detailedDescription)
            put(COLUMN_IMAGE_URL, news.imageUrl)
        }
        return try {
            db.insertWithOnConflict(TABLE_SAVED_ARTICLES, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L
        } catch (e: Exception) {
            false
        }
    }

    fun removeSavedArticle(userEmail: String, newsId: String): Boolean {
        val db = writableDatabase
        return db.delete(
            TABLE_SAVED_ARTICLES,
            "$COLUMN_USER_EMAIL = ? AND $COLUMN_NEWS_ID = ?",
            arrayOf(userEmail, newsId)
        ) > 0
    }

    fun isArticleSaved(userEmail: String, newsId: String): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT 1 FROM $TABLE_SAVED_ARTICLES WHERE $COLUMN_USER_EMAIL = ? AND $COLUMN_NEWS_ID = ?",
            arrayOf(userEmail, newsId)
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    fun getSavedArticlesForUser(userEmail: String): List<News> {
        val list = mutableListOf<News>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_SAVED_ARTICLES WHERE $COLUMN_USER_EMAIL = ? ORDER BY $COLUMN_SAVED_ID DESC",
            arrayOf(userEmail)
        )
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NEWS_ID)) ?: ""
                val title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)) ?: ""
                val content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)) ?: ""
                val location = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOCATION)) ?: ""
                val mapLocation = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MAP_LOCATION)) ?: ""
                val datetime = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATETIME)) ?: ""
                val detailedDesc = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DETAILED_DESC)) ?: ""
                val imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_URL))
                list.add(
                    News(
                        id = id,
                        title = title,
                        content = content,
                        location = location,
                        mapLocation = mapLocation,
                        datetime = datetime,
                        detailedDescription = detailedDesc,
                        imageUrl = imageUrl
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }
}
