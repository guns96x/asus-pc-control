package com.hermes.pccontrol

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.pccontrol.data.AppConfig
import com.hermes.pccontrol.data.AppPreferences
import com.hermes.pccontrol.network.LightingCapabilities
import com.hermes.pccontrol.network.MikrotikClient
import com.hermes.pccontrol.network.PcApiClient
import com.hermes.pccontrol.network.PcStatus
import com.hermes.pccontrol.network.ProcessItem
import com.hermes.pccontrol.network.TaskMetrics
import com.hermes.pccontrol.network.WolManager
import com.hermes.pccontrol.ui.AccentCyan
import com.hermes.pccontrol.ui.AccentIndigo
import com.hermes.pccontrol.ui.DarkBg
import com.hermes.pccontrol.ui.DarkSurface
import com.hermes.pccontrol.ui.DashboardScreen
import com.hermes.pccontrol.ui.PcControlTheme
import com.hermes.pccontrol.ui.SettingsScreen
import com.hermes.pccontrol.ui.TaskPerformanceScreen
import com.hermes.pccontrol.ui.TaskProcessesScreen
import com.hermes.pccontrol.ui.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefs: AppPreferences
    private val pcClient = PcApiClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
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

        // ASUS Control State
        var pcStatus by remember { mutableStateOf<PcStatus?>(null) }
        var isOnline by remember { mutableStateOf(false) }
        var isRefreshing by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf<String?>(null) }
        var lightingCapabilities by remember { mutableStateOf<LightingCapabilities?>(null) }
        var lightingBusy by remember { mutableStateOf(false) }

        // Task Manager State
        var taskMetrics by remember { mutableStateOf<TaskMetrics?>(null) }
        var processList by remember { mutableStateOf<List<ProcessItem>>(emptyList()) }
        var isTaskLoading by remember { mutableStateOf(false) }

        LaunchedEffect(config) {
            lightingCapabilities = pcClient.getLightingCapabilities(config).getOrNull()
        }

        var isCheckingUpdate by remember { mutableStateOf(false) }
        var isDownloadingUpdate by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableStateOf(0f) }

        // Background poller for ASUS Status
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

        // Background poller for Performance Metrics
        LaunchedEffect(config, currentScreen) {
            if (currentScreen == "performance") {
                while (true) {
                    val res = pcClient.getTaskMetrics(config)
                    if (res.isSuccess) {
                        taskMetrics = res.getOrNull()
                    }
                    delay(2500)
                }
            }
        }

        // Background poller for Processes List
        LaunchedEffect(config, currentScreen) {
            if (currentScreen == "processes") {
                isTaskLoading = true
                val res = pcClient.getProcesses(config)
                isTaskLoading = false
                if (res.isSuccess) {
                    processList = res.getOrNull() ?: emptyList()
                }
                while (true) {
                    delay(4000)
                    if (currentScreen == "processes") {
                        val pollRes = pcClient.getProcesses(config)
                        if (pollRes.isSuccess) {
                            processList = pollRes.getOrNull() ?: emptyList()
                        }
                    }
                }
            }
        }

        fun refreshStatus(showFeedback: Boolean = false) {
            scope.launch {
                isRefreshing = true
                val res = pcClient.getStatus(config)
                if (res.isSuccess) {
                    pcStatus = res.getOrNull()
                    isOnline = true
                    if (showFeedback) statusMessage = "ПК в мережі. Оновлено!"
                } else {
                    isOnline = false
                    if (showFeedback) statusMessage = "ПК недоступний (можливо у сні або поза мережею)"
                }
                isRefreshing = false
            }
        }

        fun refreshTaskData() {
            scope.launch {
                isTaskLoading = true
                val mRes = pcClient.getTaskMetrics(config)
                if (mRes.isSuccess) taskMetrics = mRes.getOrNull()
                val pRes = pcClient.getProcesses(config)
                if (pRes.isSuccess) processList = pRes.getOrNull() ?: emptyList()
                isTaskLoading = false
            }
        }

        Scaffold(
            bottomBar = {
                if (currentScreen != "settings") {
                    NavigationBar(
                        containerColor = DarkSurface,
                        tonalElevation = 6.dp
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == "dashboard",
                            onClick = { currentScreen = "dashboard" },
                            icon = { Icon(Icons.Default.Bolt, contentDescription = "ASUS") },
                            label = { Text("ASUS", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentCyan,
                                selectedTextColor = AccentCyan,
                                indicatorColor = AccentIndigo.copy(alpha = 0.25f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )

                        NavigationBarItem(
                            selected = currentScreen == "processes",
                            onClick = {
                                currentScreen = "processes"
                                refreshTaskData()
                            },
                            icon = { Icon(Icons.Default.List, contentDescription = "Процеси") },
                            label = { Text("Процеси", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentCyan,
                                selectedTextColor = AccentCyan,
                                indicatorColor = AccentIndigo.copy(alpha = 0.25f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )

                        NavigationBarItem(
                            selected = currentScreen == "performance",
                            onClick = {
                                currentScreen = "performance"
                                refreshTaskData()
                            },
                            icon = { Icon(Icons.Default.ShowChart, contentDescription = "Продуктивність") },
                            label = { Text("Графіки", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentCyan,
                                selectedTextColor = AccentCyan,
                                indicatorColor = AccentIndigo.copy(alpha = 0.25f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )

                        NavigationBarItem(
                            selected = currentScreen == "settings",
                            onClick = { currentScreen = "settings" },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Опції") },
                            label = { Text("Опції", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentCyan,
                                selectedTextColor = AccentCyan,
                                indicatorColor = AccentIndigo.copy(alpha = 0.25f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            },
            containerColor = DarkBg
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    "settings" -> {
                        SettingsScreen(
                            currentConfig = config,
                            onSaveConfig = { newCfg ->
                                config = newCfg
                                prefs.saveConfig(newCfg)
                                Toast.makeText(this@MainActivity, "Налаштування збережено", Toast.LENGTH_SHORT).show()
                            },
                            onBack = { currentScreen = "dashboard" }
                        )
                    }

                    "processes" -> {
                        TaskProcessesScreen(
                            processes = processList,
                            isLoading = isTaskLoading,
                            onRefresh = { refreshTaskData() },
                            onKillProcess = { pid, name ->
                                scope.launch {
                                    val res = pcClient.killProcess(config, pid)
                                    if (res.isSuccess) {
                                        Toast.makeText(this@MainActivity, res.getOrNull() ?: "Процес завершено", Toast.LENGTH_SHORT).show()
                                        refreshTaskData()
                                    } else {
                                        Toast.makeText(this@MainActivity, res.exceptionOrNull()?.message ?: "Помилка", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    }

                    "performance" -> {
                        TaskPerformanceScreen(
                            metrics = taskMetrics,
                            isLoading = isTaskLoading,
                            onRefresh = { refreshTaskData() }
                        )
                    }

                    else -> {
                        DashboardScreen(
                            config = config,
                            pcStatus = pcStatus,
                            isOnline = isOnline,
                            isRefreshing = isRefreshing,
                            statusMessage = statusMessage,
                            lightingCapabilities = lightingCapabilities,
                            onDarkMode = {
                                if (!lightingBusy) scope.launch {
                                    lightingBusy = true
                                    try {
                                        val result = pcClient.darkMode(config)
                                        statusMessage = result.getOrNull() ?: "Помилка: ${result.exceptionOrNull()?.message}"
                                        pcClient.getStatus(config).getOrNull()?.let { pcStatus = it }
                                    } finally { lightingBusy = false }
                                }
                            },
                            onRefresh = { refreshStatus(true) },
                            onOpenSettings = { currentScreen = "settings" },
                            onSleepMonitor = {
                                scope.launch {
                                    val res = pcClient.sleepMonitor(config)
                                    if (res.isSuccess) {
                                        statusMessage = "Команду вимкнення екранів прийнято"
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
                                        statusMessage = "Команду ввімкнення екранів прийнято"
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
                                        statusMessage = "Яскравість клавіатури: ${res.getOrNull()}"
                                        refreshStatus()
                                    } else {
                                        statusMessage = "Помилка підсвітки: ${res.exceptionOrNull()?.message}"
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
                                    } else {
                                        statusMessage = "Помилка підсвітки: ${res.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            onSetPerformanceMode = { mode ->
                                scope.launch {
                                    val res = pcClient.setPerformanceMode(config, mode)
                                    if (res.isSuccess) {
                                        val title = when (mode) {
                                            1 -> "Турбо"
                                            2 -> "Тихий"
                                            else -> "Баланс"
                                        }
                                        statusMessage = "Режим роботи: $title"
                                        refreshStatus()
                                    } else {
                                        statusMessage = "Помилка зміни режиму: ${res.exceptionOrNull()?.message}"
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
                                                downloadUrl = info.downloadUrl,
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
        }
    }
}
