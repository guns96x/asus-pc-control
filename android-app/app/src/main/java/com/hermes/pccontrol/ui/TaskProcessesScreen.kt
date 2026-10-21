package com.hermes.pccontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.pccontrol.network.ProcessItem

@Composable
fun TaskProcessesScreen(
    processes: List<ProcessItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onKillProcess: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") }
    var sortBy by remember { mutableStateOf("cpu") }
    var sortReverse by remember { mutableStateOf(true) }

    var selectedProcess by remember { mutableStateOf<ProcessItem?>(null) }
    var processToKillConfirm by remember { mutableStateOf<ProcessItem?>(null) }

    // Filter and sort locally for instant UI responsiveness
    val filteredProcesses = remember(processes, searchQuery, selectedCategory, sortBy, sortReverse) {
        var list = processes
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) || it.pid.toString().contains(q)
            }
        }
        if (selectedCategory != "all") {
            list = list.filter { it.category == selectedCategory }
        }
        when (sortBy) {
            "cpu" -> if (sortReverse) list.sortedByDescending { it.cpu } else list.sortedBy { it.cpu }
            "memory" -> if (sortReverse) list.sortedByDescending { it.memoryMb } else list.sortedBy { it.memoryMb }
            "disk" -> if (sortReverse) list.sortedByDescending { it.diskMbps } else list.sortedBy { it.diskMbps }
            "name" -> if (sortReverse) list.sortedByDescending { it.name.lowercase() } else list.sortedBy { it.name.lowercase() }
            else -> list
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Диспетчер завдань",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Активних процесів: ${processes.size}",
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

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Пошук за назвою або PID...", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Очистити", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = AccentIndigo,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        )

        // Category Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val categories = listOf(
                "all" to "Всі",
                "app" to "Програми",
                "background" to "Фонові",
                "windows" to "Windows"
            )
            categories.forEach { (catKey, catLabel) ->
                val selected = selectedCategory == catKey
                FilterChip(
                    selected = selected,
                    onClick = { selectedCategory = catKey },
                    label = { Text(catLabel, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentIndigo.copy(alpha = 0.25f),
                        selectedLabelColor = AccentCyan,
                        containerColor = DarkSurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = if (selected) AccentCyan else DarkBorder
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        // Table Header / Sort Bar
        Surface(
            color = DarkSurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ім'я процесу",
                    color = if (sortBy == "name") AccentCyan else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (sortBy == "name") sortReverse = !sortReverse
                            else { sortBy = "name"; sortReverse = false }
                        }
                )

                Text(
                    text = "ЦП" + if (sortBy == "cpu") (if (sortReverse) " ▼" else " ▲") else "",
                    color = if (sortBy == "cpu") AccentCyan else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .width(58.dp)
                        .clickable {
                            if (sortBy == "cpu") sortReverse = !sortReverse
                            else { sortBy = "cpu"; sortReverse = true }
                        }
                )

                Text(
                    text = "ОЗП" + if (sortBy == "memory") (if (sortReverse) " ▼" else " ▲") else "",
                    color = if (sortBy == "memory") AccentCyan else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .width(68.dp)
                        .clickable {
                            if (sortBy == "memory") sortReverse = !sortReverse
                            else { sortBy = "memory"; sortReverse = true }
                        }
                )

                Spacer(modifier = Modifier.width(36.dp))
            }
        }

        // Process List
        if (filteredProcesses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLoading) "Завантаження процесів..." else "Процесів не знайдено",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(filteredProcesses, key = { it.pid }) { proc ->
                    ProcessRowItem(
                        item = proc,
                        onClick = { selectedProcess = proc },
                        onKillClick = { processToKillConfirm = proc }
                    )
                }
            }
        }
    }

    // Detail Dialog
    selectedProcess?.let { proc ->
        AlertDialog(
            onDismissRequest = { selectedProcess = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = proc.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PID: ${proc.pid} • Категорія: ${proc.category.uppercase()}",
                        color = AccentCyan,
                        fontSize = 12.sp
                    )

                    Divider(color = DarkBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Навантаження ЦП:", color = TextSecondary, fontSize = 13.sp)
                        Text("${proc.cpu}%", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Оперативна пам'ять:", color = TextSecondary, fontSize = 13.sp)
                        Text("${proc.memoryMb.toInt()} МБ", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Швидкість диска:", color = TextSecondary, fontSize = 13.sp)
                        Text("${proc.diskMbps} МБ/с", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (proc.username.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Користувач:", color = TextSecondary, fontSize = 13.sp)
                            Text(proc.username, color = TextPrimary, fontSize = 12.sp)
                        }
                    }

                    if (proc.exe.isNotBlank()) {
                        Text("Шлях:", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = proc.exe,
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (proc.isCritical) {
                        Surface(
                            color = AccentAmber.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Критичний системний процес ядра Windows. Завершення заблоковано.",
                                color = AccentAmber,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!proc.isCritical) {
                    Button(
                        onClick = {
                            val p = proc
                            selectedProcess = null
                            processToKillConfirm = p
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                    ) {
                        Text("Зняти завдання", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedProcess = null }) {
                    Text("Закрити", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Kill Confirmation Dialog
    processToKillConfirm?.let { proc ->
        AlertDialog(
            onDismissRequest = { processToKillConfirm = null },
            title = {
                Text(
                    text = "Зняти завдання?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Завершити процес \"${proc.name}\" (PID: ${proc.pid})? Незбережені дані програми можуть бути втрачені.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = proc
                        processToKillConfirm = null
                        onKillProcess(p.pid, p.name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("Завершити", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { processToKillConfirm = null }) {
                    Text("Скасувати", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ProcessRowItem(
    item: ProcessItem,
    onClick: () -> Unit,
    onKillClick: () -> Unit
) {
    val initial = item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "P"
    val cpuColor = when {
        item.cpu >= 40f -> AccentRose
        item.cpu >= 15f -> AccentAmber
        else -> TextPrimary
    }

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Initial Badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Name + PID
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "PID: ${item.pid}",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            // CPU %
            Text(
                text = "${item.cpu}%",
                color = cpuColor,
                fontWeight = if (item.cpu >= 15f) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp,
                modifier = Modifier.width(58.dp)
            )

            // Memory MB
            Text(
                text = "${item.memoryMb.toInt()} МБ",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.width(68.dp)
            )

            // Kill Quick Button
            IconButton(
                onClick = onKillClick,
                enabled = !item.isCritical,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Зняти",
                    tint = if (item.isCritical) TextMuted.copy(alpha = 0.3f) else AccentRose.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
