package com.example.btcwidget.ui.main

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*
import androidx.compose.material.icons.filled.Settings

@Composable
fun SettingsScreen(
    currentTheme: Int,
    onThemeSelected: (Int) -> Unit,
    onModeToggle: (Int, Boolean) -> Unit,
    modeStates: List<Boolean>,
    currencySetting: String,
    onCurrencySelected: (String) -> Unit,
    refreshInterval: Int,
    onRefreshIntervalSelected: (Int) -> Unit,
    hapticEnabled: Boolean,
    onHapticToggle: (Boolean) -> Unit,
    cardBgColor: Color,
    borderColor: Color,
    valueColor: Color,
    labelColor: Color,
    subTextColor: Color
) {
    val isModern = currentTheme == 10 || currentTheme == 11
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = if (isModern) "Console Settings" else "CONSOLE SETTINGS",
                color = borderColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                letterSpacing = if (isModern) 0.sp else 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Personalization & System Settings",
                color = labelColor,
                fontSize = 13.sp,
                fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ThemeSelectorSection(
            currentTheme = currentTheme,
            onThemeSelected = onThemeSelected,
            labelColor = labelColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        PreferencesPanelCard(
            currencySetting = currencySetting,
            onCurrencySelected = onCurrencySelected,
            refreshInterval = refreshInterval,
            onRefreshIntervalSelected = onRefreshIntervalSelected,
            hapticEnabled = hapticEnabled,
            onHapticToggle = onHapticToggle,
            cardBgColor = cardBgColor,
            borderColor = borderColor,
            valueColor = valueColor,
            labelColor = labelColor,
            subTextColor = subTextColor,
            currentTheme = currentTheme
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsPanelCard(
            onModeToggle = onModeToggle,
            modeStates = modeStates,
            cardBgColor = cardBgColor,
            borderColor = borderColor,
            valueColor = valueColor,
            labelColor = labelColor,
            subTextColor = subTextColor,
            currentTheme = currentTheme
        )
    }
}

@Composable
fun SettingsPanelCard(
    onModeToggle: (Int, Boolean) -> Unit,
    modeStates: List<Boolean>,
    cardBgColor: Color,
    borderColor: Color,
    valueColor: Color,
    labelColor: Color,
    subTextColor: Color,
    currentTheme: Int
) {
    val isDynamic = currentTheme == 10
    val isIos = currentTheme == 11
    val isModern = isDynamic || isIos
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = if (isIos) RoundedCornerShape(14.dp) else if (isDynamic) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = if (isModern) (if (isIos) BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0x2BFFFFFF) else Color(0x52FFFFFF)) else null) else BorderStroke(1.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isModern) "Active Modes" else "SYSTEM SETTINGS",
                color = valueColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                letterSpacing = if (isModern) 0.sp else 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Mode Customization
            Text(
                text = "Active Widget Modes",
                color = labelColor,
                fontSize = 11.sp,
                fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))

            val modeNames = listOf(
                "BTC Price",
                "Sats per Fiat (Moscow Time)",
                "Block Height",
                "Moscow Time Clock Face",
                "MSTR BTC holdings",
                "MSTR Share-to-BTC ratio",
                "MSTR Share Price",
                "BTC Circulating Supply",
                "Halving Countdown",
                "Mempool Priority Fees",
                "Network Hash Rate",
                "Difficulty Adjustment",
                "Lightning Network Capacity"
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                modeNames.forEachIndexed { index, name ->
                    val isChecked = if (index < modeStates.size) modeStates[index] else true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (index < modeStates.size) {
                                    onModeToggle(index, !modeStates[index])
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name,
                            color = valueColor,
                            fontSize = 12.sp,
                            fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                            modifier = Modifier.weight(1f)
                        )
                        if (isModern) {
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    onModeToggle(index, checked)
                                },
                                colors = if (isIos) {
                                    SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF34C759),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = if (isSystemInDarkTheme()) Color(0xFF3A3A3C) else Color(0xFFE9E9EB)
                                    )
                                } else {
                                    SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        } else {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    onModeToggle(index, checked)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = borderColor,
                                    uncheckedColor = labelColor,
                                    checkmarkColor = cardBgColor
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreferencesPanelCard(
    currencySetting: String,
    onCurrencySelected: (String) -> Unit,
    refreshInterval: Int,
    onRefreshIntervalSelected: (Int) -> Unit,
    hapticEnabled: Boolean,
    onHapticToggle: (Boolean) -> Unit,
    cardBgColor: Color,
    borderColor: Color,
    valueColor: Color,
    labelColor: Color,
    subTextColor: Color,
    currentTheme: Int
) {
    val isDynamic = currentTheme == 10
    val isIos = currentTheme == 11
    val isModern = isDynamic || isIos
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = if (isIos) RoundedCornerShape(14.dp) else if (isDynamic) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = if (isModern) (if (isIos) BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0x2BFFFFFF) else Color(0x52FFFFFF)) else null) else BorderStroke(1.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isModern) "Preferences" else "PREFERENCES",
                color = valueColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                letterSpacing = if (isModern) 0.sp else 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Currency row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Currency",
                    color = valueColor,
                    fontSize = 12.sp,
                    fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("USD", "THB").forEach { curr ->
                        val isSelected = currencySetting == curr
                        val shape = if (isModern) RoundedCornerShape(16.dp) else RoundedCornerShape(4.dp)
                        val itemBorder = if (isModern) null else BorderStroke(1.dp, if (isSelected) borderColor else labelColor.copy(alpha = 0.4f))
                        Box(
                            modifier = Modifier
                                .then(if (itemBorder != null) Modifier.border(itemBorder, shape) else Modifier)
                                .background(
                                    color = if (isSelected) {
                                        if (isDynamic) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else if (isIos) {
                                            Color(0xFF007AFF)
                                        } else {
                                            borderColor.copy(alpha = 0.15f)
                                        }
                                    } else {
                                        if (isDynamic) {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        } else if (isIos) {
                                            if (isSystemInDarkTheme()) Color(0x551C1C1E) else Color(0x55E5E5EA)
                                        } else {
                                            Color.Transparent
                                        }
                                    },
                                    shape = shape
                                )
                                .clickable { onCurrencySelected(curr) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = curr,
                                color = if (isSelected) {
                                    if (isDynamic) MaterialTheme.colorScheme.onPrimaryContainer
                                    else if (isIos) Color.White
                                    else borderColor
                                } else {
                                    if (isDynamic) MaterialTheme.colorScheme.onSurfaceVariant
                                    else if (isIos) Color(0xFF8E8E93)
                                    else labelColor
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = if (isModern) FontFamily.Default else FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Refresh Interval row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Refresh Interval",
                    color = valueColor,
                    fontSize = 12.sp,
                    fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 60).forEach { mins ->
                        val isSelected = refreshInterval == mins
                        val displayStr = "${mins}m"
                        val shape = if (isModern) RoundedCornerShape(16.dp) else RoundedCornerShape(4.dp)
                        val itemBorder = if (isModern) null else BorderStroke(1.dp, if (isSelected) borderColor else labelColor.copy(alpha = 0.4f))
                        Box(
                            modifier = Modifier
                                .then(if (itemBorder != null) Modifier.border(itemBorder, shape) else Modifier)
                                .background(
                                    color = if (isSelected) {
                                        if (isDynamic) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else if (isIos) {
                                            Color(0xFF007AFF)
                                        } else {
                                            borderColor.copy(alpha = 0.15f)
                                        }
                                    } else {
                                        if (isDynamic) {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        } else if (isIos) {
                                            if (isSystemInDarkTheme()) Color(0x551C1C1E) else Color(0x55E5E5EA)
                                        } else {
                                            Color.Transparent
                                        }
                                    },
                                    shape = shape
                                )
                                .clickable { onRefreshIntervalSelected(mins) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = displayStr,
                                color = if (isSelected) {
                                    if (isDynamic) MaterialTheme.colorScheme.onPrimaryContainer
                                    else if (isIos) Color.White
                                    else borderColor
                                } else {
                                    if (isDynamic) MaterialTheme.colorScheme.onSurfaceVariant
                                    else if (isIos) Color(0xFF8E8E93)
                                    else labelColor
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = if (isModern) FontFamily.Default else FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Vibration & Haptic feedback row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHapticToggle(!hapticEnabled) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Vibration & Haptics",
                    color = valueColor,
                    fontSize = 12.sp,
                    fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif
                )
                if (isModern) {
                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = { checked ->
                            onHapticToggle(checked)
                        },
                        colors = if (isIos) {
                            SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF34C759),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = if (isSystemInDarkTheme()) Color(0xFF3A3A3C) else Color(0xFFE9E9EB)
                            )
                        } else {
                            SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                } else {
                    Checkbox(
                        checked = hapticEnabled,
                        onCheckedChange = { checked ->
                            onHapticToggle(checked)
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = borderColor,
                            uncheckedColor = labelColor,
                            checkmarkColor = cardBgColor
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeSelectorSection(
    currentTheme: Int,
    onThemeSelected: (Int) -> Unit,
    labelColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "Personalize Display",
            color = labelColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            val themesList = listOf(
                Triple(0, "Dark", Color(0xFF1C1C1E)),
                Triple(1, "White", Color(0xFFFFFFFF)),
                Triple(9, "Orange Pill", Color(0xFFF7931A))
            )

            themesList.forEach { (index, name, color) ->
                val isSelected = currentTheme == index
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = color,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                if (index == 1) Color.Black else Color.White
                            } else {
                                Color(0xFF8E8E93)
                            },
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { onThemeSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Text(
                            text = "✓",
                            color = if (index == 1) {
                                Color.Black
                            } else {
                                Color.White
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
