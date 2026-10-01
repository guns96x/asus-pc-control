package com.hermes.pccontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.pccontrol.BuildConfig
import com.hermes.pccontrol.data.AppConfig
import com.hermes.pccontrol.network.LightingCapabilities
import com.hermes.pccontrol.network.PcStatus

@Composable
fun DashboardScreen(
    config: AppConfig,
    pcStatus: PcStatus?,
    isOnline: Boolean,
    isRefreshing: Boolean,
    statusMessage: String?,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onSleepMonitor: () -> Unit,
    onWakeMonitor: () -> Unit,
    onSetKeyboardLevel: (Int) -> Unit,
    onToggleKeyboard: () -> Unit,
    onHibernate: () -> Unit,
    onSendLocalWol: () -> Unit,
    onSendMikrotikWol: () -> Unit,
    isCheckingUpdate: Boolean = false,
    isDownloadingUpdate: Boolean = false,
    downloadProgress: Float = 0f,
    onTriggerOtaUpdate: () -> Unit = {},
    lightingCapabilities: LightingCapabilities? = null,
    onDarkMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showHibernateDialog by remember { mutableStateOf(false) }

    if (showHibernateDialog) {
        AlertDialog(
            onDismissRequest = { showHibernateDialog = false },
            title = {
                Text(
                    text = "Глибока гібернація ПК?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Ноутбук ASUS збереже стан у hiberfil.sys і повністю вимкнеться (0W). Пробудження можливе через кнопку живлення або Wake-on-LAN пакет.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHibernateDialog = false
                        onHibernate()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("Заснути", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHibernateDialog = false }) {
                    Text("Скасувати", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated,
            textContentColor = TextPrimary
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // --- Top Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ASUS TUF Control",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = config.pcHost,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isOnline) {
                    StatusBadge(text = "Online", dotColor = AccentEmerald)
                } else {
                    StatusBadge(text = "Offline / S4", dotColor = AccentAmber)
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Оновити",
                        tint = if (isRefreshing) AccentCyan else TextSecondary
                    )
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Налаштування",
                        tint = TextSecondary
                    )
                }
            }
        }

        // --- Status Banner (if message) ---
        if (!statusMessage.isNullOrEmpty()) {
            Surface(
                color = AccentIndigo.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentIndigo.copy(alpha = 0.4f)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusMessage,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // --- Capability Note ---
        val capabilityNote = lightingCapabilities?.message
        if (!capabilityNote.isNullOrBlank()) {
            Surface(
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = capabilityNote,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // --- System Telemetry Card ---
        if (isOnline && pcStatus != null) {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (pcStatus.isAcPlugged) Icons.Default.Power else Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = if (pcStatus.isAcPlugged) "Живлення від мережі (AC)" else "Робота від батареї",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Заряд батареї: ${pcStatus.batteryPercent}%" + if (pcStatus.isCharging) " (заряджається)" else "",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    StatusBadge(
                        text = if (!pcStatus.monitorStateVerified) "Стан екранів не підтверджено" else if (pcStatus.monitorSleeping) "Екран спить" else "Екран активний",
                        dotColor = if (!pcStatus.monitorStateVerified) TextMuted else if (pcStatus.monitorSleeping) AccentAmber else AccentEmerald
                    )
                }
            }
        }

        // --- Card 1: Display Control ---
        GlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "Монітори (Вбудований + ASUS VG259)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Гасить відеосигнал Windows і вимикає DDC/CI",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroActionButton(
                    text = "Заснути екран",
                    icon = Icons.Default.NightlightRound,
                    containerColor = AccentAmber,
                    contentColor = Color.Black,
                    enabled = isOnline,
                    onClick = onSleepMonitor,
                    modifier = Modifier.weight(1f)
                )

                HeroActionButton(
                    text = "Розбудити",
                    icon = Icons.Default.WbSunny,
                    containerColor = AccentCyan,
                    contentColor = Color.Black,
                    enabled = isOnline,
                    onClick = onWakeMonitor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- Card 2: Keyboard Backlight ---
        GlassCard {
            val rawKbdLevel = pcStatus?.keyboardLevel ?: -1
            val isKbdAvailable = isOnline && rawKbdLevel >= 0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = null,
                        tint = AccentIndigo,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "Підсвітка клавіатури TUF",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isOnline && rawKbdLevel < 0) "Стан підсвітки недоступний" else "Яскравість клавіатури: 0–3",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onToggleKeyboard,
                    enabled = isKbdAvailable
                ) {
                    Icon(
                        imageVector = Icons.Default.ToggleOn,
                        contentDescription = "Перемкнути",
                        tint = if (isKbdAvailable) AccentIndigo else TextMuted,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            KeyboardLevelSelector(
                currentLevel = if (rawKbdLevel >= 0) rawKbdLevel else -1,
                onSelectLevel = { lvl ->
                    if (isKbdAvailable) {
                        onSetKeyboardLevel(lvl)
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            HeroActionButton(
                text = "Темний режим",
                icon = Icons.Default.NightlightRound,
                containerColor = AccentIndigo,
                contentColor = Color.White,
                enabled = isOnline,
                onClick = onDarkMode
            )
        }

        // --- Card 3: Deep Hibernation (S4) ---
        GlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = AccentRose,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "Глибока гібернація (S4)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Збереження оперативної пам'яті на диск (0W споживання)",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            HeroActionButton(
                text = "Загнати ПК у гібернацію",
                icon = Icons.Default.Bedtime,
                containerColor = AccentRose,
                contentColor = Color.White,
                enabled = isOnline,
                onClick = { showHibernateDialog = true }
            )
        }

        // --- Card 4: Wake-on-LAN ---
        GlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = AccentEmerald,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "Wake-on-LAN пробудження",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "MAC: ${config.pcMac}",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroActionButton(
                    text = "Wi-Fi LAN WOL",
                    icon = Icons.Default.Wifi,
                    containerColor = AccentIndigo,
                    onClick = onSendLocalWol,
                    modifier = Modifier.weight(1f)
                )

                HeroActionButton(
                    text = "MikroTik WOL",
                    icon = Icons.Default.Router,
                    containerColor = AccentEmerald,
                    onClick = onSendMikrotikWol,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- Card 5: OTA Updates ---
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "OTA Оновлення додатку",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Поточна версія: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (isDownloadingUpdate) {
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = AccentCyan,
                    trackColor = DarkSurfaceElevated,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Завантаження APK: ${(downloadProgress * 100).toInt()}%",
                    color = AccentCyan,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            HeroActionButton(
                text = if (isDownloadingUpdate) "Завантаження оновлення..." else if (isCheckingUpdate) "Перевірка оновлення..." else "Оновити додаток з ПК (OTA)",
                icon = Icons.Default.Download,
                containerColor = AccentCyan,
                contentColor = Color.Black,
                enabled = isOnline && !isDownloadingUpdate && !isCheckingUpdate,
                onClick = onTriggerOtaUpdate
            )
        }
    }
}
