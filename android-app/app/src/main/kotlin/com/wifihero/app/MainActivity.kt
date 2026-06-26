package com.wifihero.app

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.wifihero.app.databinding.ActivityMainBinding
import com.wifihero.app.model.WifiConfig
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var configManager: ConfigManager
    private lateinit var wifiRegistrar: WifiRegistrar
    private lateinit var updateChecker: UpdateChecker

    private var activeConfig: WifiConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize helpers
        configManager = ConfigManager(this)
        wifiRegistrar = WifiRegistrar(this)
        updateChecker = UpdateChecker(this)

        // Load cached configuration if present
        activeConfig = configManager.getCachedConfig()

        setupClickListeners()
        updateUiState()

        // Sync with server on startup silently
        syncConfig(silent = true)
    }

    private fun setupClickListeners() {
        // Register suggestions
        binding.btnRegisterWifi.setOnClickListener {
            val config = activeConfig
            if (config == null) {
                Toast.makeText(this, "Please sync configuration first", Toast.LENGTH_LONG).show()
                syncConfig(silent = false)
                return@setOnClickListener
            }

            binding.progressLoading.visibility = View.VISIBLE
            binding.btnRegisterWifi.isEnabled = false

            // Perform registration
            val result = wifiRegistrar.registerWifiNetworks(config)
            
            binding.progressLoading.visibility = View.GONE
            binding.btnRegisterWifi.isEnabled = true

            when (result) {
                is WifiRegistrar.RegistrationResult.Success -> {
                    val msg = getString(R.string.toast_registration_success, result.count)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    updateUiState()
                }
                is WifiRegistrar.RegistrationResult.Failure -> {
                    val msg = getString(R.string.toast_registration_failed, result.errorMessage)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Unregister suggestions
        binding.btnClearWifi.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Saved Networks?")
                .setMessage("This will remove all hostel WiFi networks suggested by wifiHERO from your device.")
                .setPositiveButton("Remove") { _, _ ->
                    val success = wifiRegistrar.unregisterAllNetworks()
                    if (success) {
                        Toast.makeText(this, R.string.toast_unregistration_success, Toast.LENGTH_SHORT).show()
                        updateUiState()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Sync config explicitly
        binding.btnSyncConfig.setOnClickListener {
            syncConfig(silent = false)
        }

        // Settings Dialog
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun syncConfig(silent: Boolean) {
        if (!silent) {
            binding.progressLoading.visibility = View.VISIBLE
            binding.btnSyncConfig.isEnabled = false
        }

        configManager.fetchConfigFromServer { config, error ->
            runOnUiThread {
                if (!silent) {
                    binding.progressLoading.visibility = View.GONE
                    binding.btnSyncConfig.isEnabled = true
                }

                if (config != null) {
                    activeConfig = config
                    if (!silent) {
                        Toast.makeText(this@MainActivity, R.string.toast_config_fetched, Toast.LENGTH_SHORT).show()
                    }
                    
                    // Check if registration was previously done with an older version of config.
                    // If so, automatically update registered networks with the new configurations.
                    val regVersion = wifiRegistrar.registeredConfigVersion
                    if (regVersion != -1 && regVersion != config.version) {
                        wifiRegistrar.registerWifiNetworks(config)
                        if (!silent) {
                            Toast.makeText(this@MainActivity, "WiFi credentials updated automatically", Toast.LENGTH_SHORT).show()
                        }
                    }

                    updateUiState()
                    checkForAppUpdates(config)
                } else {
                    if (!silent) {
                        val errMsg = error?.localizedMessage ?: "Unknown error"
                        Toast.makeText(this@MainActivity, "${getString(R.string.toast_config_failed)}: $errMsg", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun checkForAppUpdates(config: WifiConfig) {
        updateChecker.checkForUpdates(config, object : UpdateChecker.UpdateListener {
            override fun onUpdateAvailable(latestVersion: String, changelog: String, downloadUrl: String) {
                binding.txtUpdateVersion.text = "New Update Available: v$latestVersion"
                binding.txtUpdateChangelog.text = changelog.replace("\\n", "\n")
                
                binding.btnUpdate.setOnClickListener {
                    startApkDownload(downloadUrl)
                }
                
                binding.cardUpdate.visibility = View.VISIBLE
            }

            override fun onNoUpdateAvailable() {
                binding.cardUpdate.visibility = View.GONE
            }

            override fun onDownloadProgress(progress: Int) {
                binding.btnUpdate.text = "Downloading... $progress%"
            }

            override fun onDownloadSuccess(apkFile: File) {
                binding.btnUpdate.isEnabled = true
                binding.btnUpdate.text = "Install Update"
                binding.btnUpdate.setOnClickListener {
                    updateChecker.installApk(apkFile)
                }
                // Auto trigger install prompt
                updateChecker.installApk(apkFile)
            }

            override fun onDownloadFailure(e: Exception) {
                binding.btnUpdate.isEnabled = true
                binding.btnUpdate.text = "Retry Update"
                Toast.makeText(this@MainActivity, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun startApkDownload(url: String) {
        binding.btnUpdate.isEnabled = false
        binding.btnUpdate.text = "Connecting..."
        updateChecker.downloadApk(url, object : UpdateChecker.UpdateListener {
            override fun onUpdateAvailable(latestVersion: String, changelog: String, downloadUrl: String) {}
            override fun onNoUpdateAvailable() {}
            override fun onDownloadProgress(progress: Int) {
                runOnUiThread { binding.btnUpdate.text = "Downloading... $progress%" }
            }
            override fun onDownloadSuccess(apkFile: File) {
                runOnUiThread {
                    binding.btnUpdate.isEnabled = true
                    binding.btnUpdate.text = "Install"
                    binding.btnUpdate.setOnClickListener {
                        updateChecker.installApk(apkFile)
                    }
                    updateChecker.installApk(apkFile)
                }
            }
            override fun onDownloadFailure(e: Exception) {
                runOnUiThread {
                    binding.btnUpdate.isEnabled = true
                    binding.btnUpdate.text = "Retry Update"
                    Toast.makeText(this@MainActivity, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun showSettingsDialog() {
        val input = EditText(this).apply {
            setText(configManager.configUrl)
            hint = getString(R.string.hint_config_url)
            setPadding(50, 40, 50, 40)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_settings_title)
            .setMessage(R.string.dialog_settings_help)
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotBlank()) {
                    configManager.configUrl = newUrl
                    Toast.makeText(this, R.string.toast_settings_saved, Toast.LENGTH_SHORT).show()
                    syncConfig(silent = false)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUiState() {
        val count = wifiRegistrar.registeredCount
        val version = wifiRegistrar.registeredConfigVersion

        binding.txtRegisteredCount.text = count.toString()
        binding.txtConfigVersion.text = if (version == -1) "N/A" else "v$version"

        if (count > 0) {
            // Registered active state
            binding.txtStatusTitle.text = getString(R.string.status_registered)
            binding.txtStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.success))
            binding.txtStatusDesc.text = getString(R.string.msg_registered)
            
            setIndicatorColor(ContextCompat.getColor(this, R.color.success))
            binding.btnClearWifi.visibility = View.VISIBLE
            binding.btnRegisterWifi.text = "Re-register WiFi Configurations"
        } else {
            // Unregistered state
            binding.txtStatusTitle.text = getString(R.string.status_not_registered)
            binding.txtStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            binding.txtStatusDesc.text = getString(R.string.msg_unregistered)
            
            setIndicatorColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            binding.btnClearWifi.visibility = View.GONE
            binding.btnRegisterWifi.text = getString(R.string.btn_register_all)
        }

        // Show update card if updates already found in background
        val config = activeConfig
        if (config != null) {
            checkForAppUpdates(config)
        }
    }

    private fun setIndicatorColor(color: Int) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize(12, 12)
            setColor(color)
        }
        binding.viewStatusIndicator.background = drawable
    }
}
