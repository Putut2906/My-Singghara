package com.example.ui.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class AppMetadata(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val route: String? = null,
    val gameSection: String? = null
)

object AppRegistry {
    val allAppsMap = mapOf(
        "data_karyawan_murid" to AppMetadata("data_karyawan_murid", "Company", Icons.Default.Badge, Color(0xFF0F766E), route = "data_karyawan_murid"),
        "quran" to AppMetadata("quran", "Al-Qur'an", Icons.AutoMirrored.Filled.MenuBook, Color(0xFF00796B), route = "quran"),
        "pembukuan_usaha" to AppMetadata("pembukuan_usaha", "Pembukuan", Icons.Default.TrendingUp, Color(0xFFE65100), route = "pembukuan_usaha"),
        "mindmap" to AppMetadata("mindmap", "MindMap AI", Icons.AutoMirrored.Filled.MenuBook, Color(0xFF0288D1), route = "mindmap"),
        "pemutar_musik" to AppMetadata("pemutar_musik", "Musik", Icons.Default.MusicNote, Color(0xFFE91E63), route = "pemutar_musik"),
        "block_puzzle" to AppMetadata("block_puzzle", "Block Puzzle", Icons.Default.GridOn, Color(0xFF2C5E6A), gameSection = "block_puzzle"),
        "tetris" to AppMetadata("tetris", "Brick Game", Icons.Default.Gamepad, Color(0xFF306230), gameSection = "tetris"),
        "undian_spinner" to AppMetadata("undian_spinner", "Spinner", Icons.Default.Refresh, Color(0xFF8338EC), gameSection = "undian_spinner"),
        "jadwal_harian" to AppMetadata("jadwal_harian", "Jadwal Harian", Icons.Default.AccessTime, Color(0xFF43A047), route = "jadwal_harian")
    )

    val defaultOrder = listOf("data_karyawan_murid", "quran", "pembukuan_usaha", "mindmap", "pemutar_musik", "block_puzzle", "tetris", "undian_spinner", "jadwal_harian")
}
