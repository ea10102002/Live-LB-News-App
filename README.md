# Live.LB — Native Android News & Security Tracker Application

**Author:** Edwen Aouad  
**Institution:** Université Saint-Joseph de Beyrouth (USJ)  
**Course:** Mobile Application Development (Android / Kotlin)  
**Instructor:** Dr. Fakhry  

---

## 1. Project Overview & Intended Users

**Live.LB** is a native Android application built in **Kotlin** designed to provide Lebanese citizens, journalists, students, and international observers with a unified, real-time platform for consuming local and international news while tracking security and strike events across Lebanon on an interactive map.

### Intended Users
* **Everyday Readers in Lebanon & the Diaspora:** Users who want live local coverage from **LBCI** and **MTV Lebanon** alongside **International News** in one unified interface.
* **Bilingual & Foreign Readers:** Readers who benefit from instant **English ↔ Arabic AI translation** on full news articles.
* **Security-Conscious Citizens:** Users monitoring real-time security alerts and mapped strike locations across Lebanese regions.
* **Application Administrators:** System managers who oversee registered user accounts and broadcast urgent breaking-news notifications.

---

## 2. Core Features Implemented

1. **Mandatory User Authentication & Profile Management (`SQLite` + `SharedPreferences`):**
   * Secure **Sign-Up** and **Sign-In** workflows with input validation (letters-only names, `@gmail.com` / `@hotmail.com` email verification, 8+ character passwords, and auto-formatted `DD/MM/YYYY` birthdates with age validation).
   * Users can view and edit their personal profile details (First Name, Last Name, Email, Birth Date, Password) and manage their personal **Saved Articles (`🔖`)** stored in SQLite (`UserDatabase.db`).

2. **Dynamic Local (LBCI & MTV Lebanon) + International News Feed:**
   * **All Lebanon Tab:** Combines live Lebanon coverage from **LBCI** and **MTV Lebanon** with Lebanon-filtered articles from **NewsAPI.org**, strictly sorted chronologically (newest first).
   * **LBCI Tab:** Live Lebanon news scraped directly from LBCI (`lbcgroup.tv`) using **Jsoup**, complete with article images and full-article body loading.
   * **MTV Lebanon Tab:** Live chronological local news scraped directly from MTV Lebanon (`mtv.com.lb/news/محليات`).
   * **International Tab:** Global news powered by **Retrofit + Gson** via `NewsAPI.org`.
   * **Saved (`🔖`) Tab:** Per-user bookmarked articles persisted in SQLite for offline reading.
   * Includes **45-second silent auto-refresh**, **Pull-to-Refresh (`SwipeRefreshLayout`)**, **Dark / Light Mode Toggle**, and **Bilingual UI Toggle (`عربي` / `EN`)**.

3. **✨ AI Article Translation (`English ↔ Arabic`) with Google Gemini:**
   * Inside the **Article Detail View (`NewsDetailPage`)**, users can tap **"✨ Translate with Gemini AI (EN ↔ AR)"** to translate the headline, summary, and full multi-paragraph article body between Arabic and English in real time, with a 1-tap toggle back to the original language.

4. **Interactive Strike & Security Map (`Jetpack Compose` + `Google Maps SDK`):**
   * Automatically scans incoming English and Arabic news reports (`StrikeLocationDetector`) for kinetic security events (airstrikes, shelling, drone strikes, raids) and maps **20+ Lebanese towns and districts** (*Nabatieh, Mayfadoun, Tyre, Khiam, Marjayoun, Bint Jbeil, Naqoura, Baalbek, Janta, Dahieh, Sidon*, etc.) to accurate GPS coordinates.
   * Displays custom markers on an interactive Google Map centered on Lebanon, with a live active-report counter and clickable detail popups.

5. **Admin Control Center (`AdminDashboardActivity`):**
   * Authenticated Admin login directly from the Sign-In screen (`admin` / `admin`).
   * Displays total registered user statistics, interactive user cards with 1-tap **Edit (`✏️`)** and **Delete (`🗑️`)** actions, full SQLite CRUD (Add, Update, Delete users including `DD/MM/YYYY` DOB), and a 1-tap **Breaking Alert Broadcast** tool.

6. **Background Notifications (`WorkManager` & `CoroutineWorker`):**
   * Uses `NewsNotificationWorker` to check for new breaking articles in the background (`PeriodicWorkRequest` + `OneTimeWorkRequest` for instant demo testing).
   * Requests Android 13+ (`POST_NOTIFICATIONS`) runtime permission and attaches a `PendingIntent` so tapping any notification opens `NewsDetailPage` directly to that article.

---

## 3. Technical Architecture & Stack

* **Language:** Kotlin (`minSdk = 24`, `targetSdk = 34`, `compileSdk = 34`)
* **Architecture Pattern:** MVVM (`ViewModel`, `LiveData`, `Repository`)
* **UI Toolkit:** Hybrid XML (`MaterialComponents`, `CoordinatorLayout`, `RecyclerView`, `CardView`) + **Jetpack Compose** (`GoogleMap` in `MapPage.kt`)
* **Networking & Parsing:**
  * **Retrofit 2 + OkHttp + Gson Converter:** REST API communication with `NewsAPI.org`
  * **Jsoup (`1.17.2`):** Live HTML & JSON-LD scraping for **LBCI** and **MTV Lebanon**
  * **Gemini AI Translation (`GeminiTranslator.kt`):** Bidirectional EN ↔ AR full-text translation
* **Local Persistence:**
  * **SQLite (`DatabaseHelper.kt`):** Relational tables `users` and `saved_articles`
  * **SharedPreferences (`NewsPreferencesManager.kt` & `UserSession`):** Offline news caching and active login session management
* **Background Processing:** Android Jetpack `WorkManager` (`CoroutineWorker`)
* **Image Loading:** Square **Picasso**

---

## 4. How to Build and Run the Application

### Prerequisites
* **Android Studio** (Iguana / Jellyfish / Koala / Ladybug or newer)
* **JDK 17** (bundled with Android Studio)
* **Android SDK Platform 34** installed via SDK Manager
* API Keys configured locally (`YOUR_GOOGLE_MAPS_API_KEY`, `YOUR_NEWS_API_KEY`, and `GEMINI_API_KEY`)

### Step-by-Step Setup Instructions

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/ea10102002/Live-LB-News-App.git
   cd Live-LB-News-App
   ```

2. **Open in Android Studio:**
   * Launch **Android Studio** → **File → Open** and select the project root folder.
   * Allow Gradle to sync automatically (Android Studio will generate `local.properties` pointing to your local Android SDK).

3. **Configure API Keys (Local Testing):**
   * Set your Google Maps API key in `AndroidManifest.xml`, `NewsApplication.kt`, and `MainActivity.kt`.
   * Set your NewsAPI key in `NewsApiService.kt` and your Gemini API key via `GEMINI_API_KEY` in `GeminiTranslator.kt`.

4. **Connect a Device or Emulator:**
   * **Physical Android Phone (Recommended):** Enable **Developer Options → USB Debugging** (or **Wireless Debugging**) and connect your phone.
   * **Android Emulator:** Create/start a Pixel device running **Android 13 (API 33)** or **Android 14 (API 34)** with Google Play Services enabled.

5. **Run the Application:**
   * Click the green **Run (`▶`)** button in Android Studio, or run from the terminal:
     ```bash
     ./gradlew installDebug
     ```

---

## 5. Demo & Testing Credentials

* **Standard User Account:**
  * Tap **"Don't have an account? Create Account"** on the Login screen to register a new user (e.g., `edwen2002aouad@gmail.com`, password `12345678`, DOB `10/10/2002`).
* **Administrator Account (Opens Admin Control Center):**
  * **Email / Username:** `admin` (or `admin@gmail.com`)
  * **Password:** `admin` (or `admin123`)
* **Testing Background Notifications Live:**
  * Log in and open **My Profile** (top-right user icon) → tap **"🔔 Trigger Breaking Alert (WorkManager)"** (or tap **"🔔 Push Alert Now"** inside the Admin Panel) to immediately fire a background notification without waiting for the 15-minute OS periodic interval.
