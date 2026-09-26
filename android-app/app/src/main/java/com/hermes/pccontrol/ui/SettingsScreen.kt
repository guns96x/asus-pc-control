package com.hermes.pccontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.pccontrol.data.AppConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentConfig: AppConfig,
    onSaveConfig: (AppConfig) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pcHost by remember { mutableStateOf(currentConfig.pcHost) }
    var pcFallbackHost by remember { mutableStateOf(currentConfig.pcFallbackHost) }
    var pcPort by remember { mutableStateOf(currentConfig.pcPort.toString()) }
    var authToken by remember { mutableStateOf(currentConfig.authToken) }
    var pcMac by remember { mutableStateOf(currentConfig.pcMac) }

    var mikrotikHost by remember { mutableStateOf(currentConfig.mikrotikHost) }
    var mikrotikPort by remember { mutableStateOf(currentConfig.mikrotikPort.toString()) }
    var mikrotikUseHttps by remember { mutableStateOf(currentConfig.mikrotikUseHttps) }
    var mikrotikUser by remember { mutableStateOf(currentConfig.mikrotikUser) }
    var mikrotikPass by remember { mutableStateOf(currentConfig.mikrotikPass) }
    var mikrotikInterface by remember { mutableStateOf(currentConfig.mikrotikInterface) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Назад",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "Налаштування",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            IconButton(
                onClick = {
                    val newCfg = AppConfig(
                        pcHost = pcHost,
                        pcFallbackHost = pcFallbackHost,
                        pcPort = pcPort.toIntOrNull() ?: 8765,
                        authToken = authToken,
                        pcMac = pcMac,
                        mikrotikHost = mikrotikHost,
                        mikrotikPort = mikrotikPort.toIntOrNull() ?: 443,
                        mikrotikUseHttps = mikrotikUseHttps,
                        mikrotikUser = mikrotikUser,
                        mikrotikPass = mikrotikPass,
                        mikrotikInterface = mikrotikInterface
                    )
                    onSaveConfig(newCfg)
                    onBack()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Зберегти",
                    tint = AccentEmerald
                )
            }
        }

        // Section 1: PC Settings
        GlassCard {
            Text(
                text = "Параметри ПК (LAN / Tailscale)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = pcHost,
                onValueChange = { pcHost = it },
                label = { Text("Основна IP ПК (LAN, напр. 192.168.80.200)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = pcFallbackHost,
                onValueChange = { pcFallbackHost = it },
                label = { Text("Резервна IP (Tailscale, напр. 100.82.252.86)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = pcPort,
                onValueChange = { pcPort = it },
                label = { Text("Порт агента (default: 8765)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = authToken,
                onValueChange = { authToken = it },
                label = { Text("Bearer Auth Token") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = pcMac,
                onValueChange = { pcMac = it },
                label = { Text("MAC адреса ПК (Realtek GbE)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Section 2: MikroTik REST Settings
        GlassCard {
            Text(
                text = "MikroTik REST API (Tailscale / LAN)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AccentEmerald
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = mikrotikHost,
                onValueChange = { mikrotikHost = it },
                label = { Text("MikroTik IP / Hostname") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = mikrotikPort,
                    onValueChange = { mikrotikPort = it },
                    label = { Text("Порт (443 / 80)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = mikrotikInterface,
                    onValueChange = { mikrotikInterface = it },
                    label = { Text("Інтерфейс (bridge)") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Використовувати HTTPS (SSL)", color = TextPrimary, fontSize = 14.sp)
                Switch(checked = mikrotikUseHttps, onCheckedChange = { mikrotikUseHttps = it })
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = mikrotikUser,
                onValueChange = { mikrotikUser = it },
                label = { Text("Користувач MikroTik (wol-bot)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = mikrotikPass,
                onValueChange = { mikrotikPass = it },
                label = { Text("Пароль MikroTik") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
        }

        HeroActionButton(
            text = "Зберегти налаштування",
            icon = Icons.Default.Check,
            containerColor = AccentIndigo,
            onClick = {
                val newCfg = AppConfig(
                    pcHost = pcHost,
                    pcFallbackHost = pcFallbackHost,
                    pcPort = pcPort.toIntOrNull() ?: 8765,
                    authToken = authToken,
                    pcMac = pcMac,
                    mikrotikHost = mikrotikHost,
                    mikrotikPort = mikrotikPort.toIntOrNull() ?: 443,
                    mikrotikUseHttps = mikrotikUseHttps,
                    mikrotikUser = mikrotikUser,
                    mikrotikPass = mikrotikPass,
                    mikrotikInterface = mikrotikInterface
                )
                onSaveConfig(newCfg)
                onBack()
            }
        )
    }
}
