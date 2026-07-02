# wifiHERO 

A premium, secure hostel WiFi auto-connector and roaming manager for Android. 

wifiHERO solves the annoying problem of having to repeatedly type the WiFi password when moving between different rooms in a hostel or campus with multiple access points sharing a common password (e.g. `Amrita-2.4G-101`, `Amrita-5G-808`, etc.).

---

## How It Works

1. **Android App**: Utilizes Android's official `WifiNetworkSuggestion` API (Android 10+) to batch-register all hostel room WiFi credentials (both 2.4G & 5G bands) with a single tap. Once suggested, the Android OS handles background scanning and automatically connects to the strongest room WiFi node as you roam.
2. **Admin Web Console**: A sleek, dark-themed admin dashboard that runs entirely in the browser (hosted on GitHub Pages). The administrator can manage floor ranges, update WiFi passwords, change SSID prefixes, and save a `wifi-config.json` configuration file.
3. **Github Serverless Backend**: The JSON config file is hosted in your public GitHub repository. The Android app fetches it securely over HTTPS. When the admin updates the password or rooms via the GitHub repository, the users' apps automatically pick it up and re-register the connections in the background without needing a reinstall!

---

##  Setup Instructions for the Administrator (You!)

### Step 1: Create a GitHub Repository
1. Go to [GitHub](https://github.com) and create a **public** repository named `wifihero` (or any name you prefer).
2. Push all the contents of the local `wifiHERO` folder (which contains this README) to your repository.
   ```bash
   git init
   git add .
   git commit -m "Initial commit of wifiHERO"
   git branch -M main
   git remote add origin https://github.com/your-username/wifihero.git
   git push -u origin main
   ```

### Step 2: Enable the Admin Web Panel (GitHub Pages)
1. In your GitHub repository, click on **Settings** (top tabs).
2. Go to **Pages** (under Code and automation in the left sidebar).
3. Under **Build and deployment**, select **Deploy from a branch** for Source.
4. Select **Branch: `main`** and folder `/ (root)` or `admin-panel` depending on how you structured it. (Since the admin panel is in the `admin-panel/` directory, you can select root or main and access it at `https://your-username.github.io/wifihero/admin-panel/`).
5. Click **Save**. Within a minute, your Admin Panel will be live!

### Step 3: Get Your Raw JSON Config Link
1. Go to your repository files on GitHub.
2. Click on `config/wifi-config.json`.
3. Click the **Raw** button on the top-right of the file content.
4. Copy the URL from your browser's address bar. It will look like this:
   `https://raw.githubusercontent.com/your-username/wifihero/main/config/wifi-config.json`
5. This is the URL you will enter in the settings of the Android app.

---

##  How to Compile the APK (Automated via CI/CD)

You **do not need Android Studio** or Gradle installed on your computer. GitHub will build the APK for you for free!

1. Every time you push code or configuration changes to the `main` branch, a GitHub Action starts automatically.
2. Click on the **Actions** tab in your GitHub repository.
3. You will see a workflow running named **Build Android APK**.
4. Once it finishes (takes about 2-3 minutes), click on the completed run.
5. Scroll down to the **Artifacts** section at the bottom.
6. Download the `wifihero-debug-apk` zip file. Extract it to get `app-debug.apk` (this is the sideloadable app).
7. Share this `app-debug.apk` with the hostel students (via WhatsApp, Telegram, etc.)!

---

##  User Instructions (How Students Install & Use)

1. **Install the APK**:
   - Download the APK on your Android phone.
   - Tap to install. Android will warn about installing "unknown apps". Go to Settings and toggle **Allow from this source** (since it is sideloaded).
2. **First Time Setup**:
   - Open **wifiHERO**.
   - Tap **Sync Config** to load the default room layouts and passwords.
   - *(Optional for custom setups)* Tap the **Settings icon** (top-right) and enter your hostel's raw config URL, then tap Save.
3. **Register WiFi**:
   - Tap **Connect All Rooms**.
   - A system notification or dialogue will appear asking you: *"Allow wifiHERO to suggest Wi-Fi networks?"*
   - Tap **Allow** (or **Agree**).
   - Done! Your phone now knows all 300+ rooms' WiFi networks.

---

##  Administrative Password

The Admin Web Panel is protected with a password.
- **Default password**: `harshaThe legend@123`
- To change this password:
  1. Pick a new password.
  2. Compute its SHA-256 hash (you can use online generators).
  3. Edit `admin-panel/script.js` and update the `ADMIN_PASSWORD_HASH` constant on Line 2 with your new hash:
     ```javascript
     const ADMIN_PASSWORD_HASH = "your-new-sha256-hash-here";
     ```
  4. Commit and push to GitHub.
