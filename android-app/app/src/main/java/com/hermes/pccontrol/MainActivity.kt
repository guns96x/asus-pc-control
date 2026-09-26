package com.hermes.pccontrol

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.hermes.pccontrol.data.AppConfig
import com.hermes.pccontrol.data.AppPreferences
import com.hermes.pccontrol.network.MikrotikClient
import com.hermes.pccontrol.network.PcApiClient
import com.hermes.pccontrol.network.PcStatus
import com.hermes.pccontrol.network.WolManager
import com.hermes.pccontrol.ui.DashboardScreen
import com.hermes.pccontrol.ui.DarkBg
import com.hermes.pccontrol.ui.PcControlTheme
import com.hermes.pccontrol.ui.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefs: AppPreferences
    private val pcClient = PcApiClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = AppPreferences(this)

        setContent {
            PcControlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBg
                ) {
                    MainApp()
                }
            }
        }
    }

    @Composable
    fun MainApp() {
        val scope = rememberCoroutineScope()
        var config by remember { mutableStateOf(prefs.loadConfig()) }
        var currentScreen by remember { mutableStateOf("dashboard") }

        var pcStatus by remember { mutableStateOf<PcStatus?>(null) }
        var isOnline by remember { mutableStateOf(false) }
        var isRefreshing by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf<String?>(null) }

        var isCheckingUpdate by remember { mutableStateOf(false) }
        var isDownloadingUpdate by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableStateOf(0f) }

        // Background poller
        LaunchedEffect(config) {
            while (true) {
                val res = pcClient.getStatus(config)
                if (res.isSuccess) {
                    pcStatus = res.getOrNull()
                    isOnline = true
                } else {
                    isOnline = false
                }
                delay(4000)
            }
        }

        fun refreshStatus() {
            scope.launch {
                isRefreshing = true
                val res = pcClient.getStatus(config)
                if (res.isSuccess) {
                    pcStatus = res.getOrNull()
                    isOnline = true
                    statusMessage = "ПК в мережі. Оновлено!"
                } else {
                    isOnline = false
                    statusMessage = "ПК недоступний (можливо у сні або поза мережею)"
                }
                isRefreshing = false
            }
        }

        if (currentScreen == "settings") {
            SettingsScreen(
                currentConfig = config,
                onSaveConfig = { newCfg ->
                    config = newCfg
                    prefs.saveConfig(newCfg)
                    Toast.makeText(this, "Налаштування збережено", Toast.LENGTH_SHORT).show()
                },
                onBack = { currentScreen = "dashboard" }
            )
        } else {
            DashboardScreen(
                config = config,
                pcStatus = pcStatus,
                isOnline = isOnline,
                isRefreshing = isRefreshing,
                statusMessage = statusMessage,
                onRefresh = { refreshStatus() },
                onOpenSettings = { currentScreen = "settings" },
                onSleepMonitor = {
                    scope.launch {
                        val res = pcClient.sleepMonitor(config)
                        if (res.isSuccess) {
                            statusMessage = "Монітори переведено в режим сну (DDC/CI off)"
                            refreshStatus()
                        } else {
                            statusMessage = "Помилка вимикання екрана: ${res.exceptionOrNull()?.message}"
                        }
                    }
                },
                onWakeMonitor = {
                    scope.launch {
                        val res = pcClient.wakeMonitor(config)
                        if (res.isSuccess) {
                            statusMessage = "Монітори розбуджено"
                            refreshStatus()
                        } else {
                            statusMessage = "Помилка пробудження екрана: ${res.exceptionOrNull()?.message}"
                        }
                    }
                },
                onSetKeyboardLevel = { lvl ->
                    scope.launch {
                        val res = pcClient.setKeyboardLevel(config, lvl)
                        if (res.isSuccess) {
                            statusMessage = "Яскравість клавіатури: $lvl"
                            refreshStatus()
                        }
                    }
                },
                onToggleKeyboard = {
                    scope.launch {
                        val res = pcClient.toggleKeyboard(config)
                        if (res.isSuccess) {
                            val newLvl = res.getOrNull() ?: 0
                            statusMessage = "Підсвітка перемкнута на $newLvl"
                            refreshStatus()
                        }
                    }
                },
                onHibernate = {
                    scope.launch {
                        val res = pcClient.hibernate(config)
                        if (res.isSuccess) {
                            statusMessage = "ПК переходить у глибоку гібернацію..."
                            isOnline = false
                        } else {
                            statusMessage = "Помилка гібернації: ${res.exceptionOrNull()?.message}"
                        }
                    }
                },
                onSendLocalWol = {
                    scope.launch {
                        statusMessage = "Відправка Wi-Fi WOL broadcast..."
                        val res = WolManager.sendMagicPacket(config.pcMac)
                        statusMessage = if (res.isSuccess) {
                            res.getOrNull()
                        } else {
                            "Помилка WOL: ${res.exceptionOrNull()?.message}"
                        }
                    }
                },
                onSendMikrotikWol = {
                    scope.launch {
                        statusMessage = "Відправка WOL через MikroTik REST API..."
                        val res = MikrotikClient.sendWol(config)
                        statusMessage = if (res.isSuccess) {
                            res.getOrNull()
                        } else {
                            "Помилка MikroTik: ${res.exceptionOrNull()?.message}"
                        }
                    }
                },
                isCheckingUpdate = isCheckingUpdate,
                isDownloadingUpdate = isDownloadingUpdate,
                downloadProgress = downloadProgress,
                onTriggerOtaUpdate = {
                    scope.launch {
                        isCheckingUpdate = true
                        statusMessage = "Перевірка оновлення на ПК..."
                        val checkRes = com.hermes.pccontrol.network.OtaManager.checkUpdate(config)
                        isCheckingUpdate = false

                        if (checkRes.isSuccess) {
                            val info = checkRes.getOrNull()!!
                            val currentVersionCode = try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                    packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
                                } else {
                                    @Suppress("DEPRECATION")
                                    packageManager.getPackageInfo(packageName, 0).versionCode
                                }
                            } catch (e: Exception) {
                                1
                            }

                            if (info.versionCode > currentVersionCode) {
                                statusMessage = "Знайдено оновлення ${info.versionName} (build ${info.versionCode})! Завантаження..."
                                isDownloadingUpdate = true
                                downloadProgress = 0f

                                val dlRes = com.hermes.pccontrol.network.OtaManager.downloadAndInstallApk(
                                    context = this@MainActivity,
                                    config = config,
                                    onProgress = { p -> downloadProgress = p }
                                )
                                isDownloadingUpdate = false

                                if (dlRes.isSuccess) {
                                    statusMessage = "Оновлення завантажено. Встановіть через системний діалог."
                                } else {
                                    statusMessage = "Помилка оновлення: ${dlRes.exceptionOrNull()?.message}"
                                }
                            } else {
                                statusMessage = "Встановлено актуальну версію (${info.versionName}, build $currentVersionCode). Оновлень немає."
                            }
                        } else {
                            statusMessage = "Помилка перевірки оновлень: ${checkRes.exceptionOrNull()?.message}"
                        }
                    }
                }
            )
        }
    }
}
