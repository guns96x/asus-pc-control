package com.hermes.pccontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.pccontrol.network.TaskMetrics

@Composable
fun TaskPerformanceScreen(
    metrics: TaskMetrics?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Продуктивність",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Телеметрія ASUS TUF F15 у реальному часі",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AccentCyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Оновити",
                        tint = AccentCyan
                    )
                }
            }
        }

        if (metrics == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLoading) "Отримання метрик ПК..." else "ПК недоступний",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            // 1. CPU CARD
            PerformanceCard(
                title = "ЦП (Процесор)",
                subtitle = metrics.cpuModel,
                accentColor = Color(0xFF60CDFF),
                mainValue = "${metrics.cpuTotal.toInt()}%",
                mainLabel = "${metrics.cpuFreqGhz} ГГц"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Temperature and Fan row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricPill(
                            icon = "🔥",
                            label = "Температура ЦП",
                            value = "${metrics.cpuTempC} °C",
                            color = if (metrics.cpuTempC >= 85) AccentRose else AccentAmber,
                            modifier = Modifier.weight(1f)
                        )
                        MetricPill(
                            icon = "🌪",
                            label = "Кулер ЦП",
                            value = "${metrics.cpuFanRpm} RPM",
                            color = AccentCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (metrics.cpuTotal / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF60CDFF),
                        trackColor = DarkSurfaceElevated
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem("Ядра", "${metrics.coresPhysical} / ${metrics.coresLogical}")
                        DetailItem("Процеси", "${metrics.processesCount}")
                        DetailItem("Потоки", "${metrics.threadsCount}")
                        DetailItem("Uptime", formatUptimeSec(metrics.uptimeSec))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. GPU CARD (NVIDIA GeForce RTX 3050)
            PerformanceCard(
                title = "Відеокарта (GPU)",
                subtitle = metrics.gpuName,
                accentColor = Color(0xFF76B900),
                mainValue = "${metrics.gpuLoad.toInt()}%",
                mainLabel = "3D Навантаження"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Temperature and Fan row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricPill(
                            icon = "🟢",
                            label = "Температура GPU",
                            value = "${metrics.gpuTempC} °C",
                            color = Color(0xFF76B900),
                            modifier = Modifier.weight(1f)
                        )
                        MetricPill(
                            icon = "🌪",
                            label = "Кулер GPU",
                            value = "${metrics.gpuFanRpm} RPM",
                            color = AccentCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (metrics.gpuLoad / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF76B900),
                        trackColor = DarkSurfaceElevated
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem("Пам'ять VRAM", "${metrics.gpuVramUsedMb.toInt()} / ${metrics.gpuVramTotalMb.toInt()} МБ")
                        DetailItem("Використання", "${((metrics.gpuVramUsedMb / metrics.gpuVramTotalMb.coerceAtLeast(1f)) * 100).toInt()}%")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. RAM CARD
            PerformanceCard(
                title = "Пам'ять (RAM)",
                subtitle = "Загальний обсяг: ${metrics.ramTotalGb} ГБ",
                accentColor = Color(0xFFB180FF),
                mainValue = "${metrics.ramPercent.toInt()}%",
                mainLabel = "${metrics.ramUsedGb} / ${metrics.ramTotalGb} ГБ"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { (metrics.ramPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFFB180FF),
                        trackColor = DarkSurfaceElevated
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem("Використано", "${metrics.ramUsedGb} ГБ")
                        DetailItem("Доступно", "${metrics.ramAvailGb} ГБ")
                        DetailItem("Завантаження", "${metrics.ramPercent.toInt()}%")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. DISK & NETWORK DUAL ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Disk
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Диск (I/O)", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(metrics.diskReadMb + metrics.diskWriteMb).format(1)} МБ/с",
                            color = Color(0xFF10B981),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Читання: ${metrics.diskReadMb.format(1)} МБ/с", color = TextMuted, fontSize = 10.sp)
                        Text("Запис: ${metrics.diskWriteMb.format(1)} МБ/с", color = TextMuted, fontSize = 10.sp)
                    }
                }

                // Network
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Мережа", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${metrics.netRecvMbps.format(1)} Мбіт/с",
                            color = Color(0xFFF59E0B),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Вхідний: ${metrics.netRecvMbps.format(1)} Мбіт/с", color = TextMuted, fontSize = 10.sp)
                        Text("Вихідний: ${metrics.netSentMbps.format(1)} Мбіт/с", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PerformanceCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    mainValue: String,
    mainLabel: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = subtitle, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = mainValue, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text = mainLabel, color = TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun MetricPill(
    icon: String,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = icon, fontSize = 16.sp)
            Column {
                Text(text = label, color = TextMuted, fontSize = 10.sp)
                Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column {
        Text(text = label, color = TextMuted, fontSize = 10.sp)
        Text(text = value, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun Float.format(digits: Int): String {
    return String.format(java.util.Locale.US, "%.${digits}f", this)
}

private fun formatUptimeSec(sec: Long): String {
    val d = sec / 86400
    val h = (sec % 86400) / 3600
    val m = (sec % 3600) / 60
    return if (d > 0) "${d}д ${h}г" else "${h}г ${m}хв"
}
