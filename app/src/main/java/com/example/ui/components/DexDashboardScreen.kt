package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.models.AppMetadata
import com.example.ui.models.AppRegistry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dashboard native untuk Samsung DeX: meniru tata letak halaman Web Connect PC
 * (baris kartu statistik + grid tile aplikasi) memakai UI/UX My Singghara sendiri.
 * Murni Compose, tanpa WebView/localhost. Hanya layout & styling; navigasi memakai
 * viewModel.currentRoute yang sama dengan layar lain.
 */
@Composable
fun DexDashboardScreen(viewModel: MainViewModel) {
    val colors = rememberDexWebColors()
    val notes by viewModel.notes.collectAsState()
    val agendas by viewModel.agendaItems.collectAsState()
    val shopping by viewModel.shoppingItems.collectAsState()

    val stats = listOf(
        Triple("Catatan", notes.size.toString(), Icons.Default.EditNote),
        Triple("Agenda", agendas.size.toString(), Icons.Default.DateRange),
        Triple("Item Belanja", shopping.size.toString(), Icons.Default.ShoppingCart),
        Triple("Aplikasi", AppRegistry.allAppsMap.size.toString(), Icons.Default.GridOn)
    )

    val today = remember {
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(Date())
    }

    val apps = remember { AppRegistry.defaultOrder.mapNotNull { AppRegistry.allAppsMap[it] } }
    val columnsPerRow = 6

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgMain)
            .verticalScroll(rememberScrollState())
            .padding(28.dp)
    ) {
        Text(
            text = "Halo, ${viewModel.profileName.ifEmpty { "Pengguna" }}",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textMain
        )
        Spacer(Modifier.height(6.dp))
        Text(text = today, fontSize = 13.sp, color = colors.textSub)
        Spacer(Modifier.height(22.dp))

        // Baris kartu statistik (multi-kolom)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            stats.forEach { (label, value, icon) ->
                DexStatCard(label = label, value = value, icon = icon, colors = colors, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Aplikasi", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textMain)
        Spacer(Modifier.height(14.dp))

        apps.chunked(columnsPerRow).forEach { rowApps ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                rowApps.forEach { app ->
                    DexAppTile(
                        app = app,
                        colors = colors,
                        modifier = Modifier.weight(1f),
                        onOpen = { viewModel.currentRoute = app.route ?: "game_list" }
                    )
                }
                repeat(columnsPerRow - rowApps.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DexStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    colors: DexWebColors,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val cardBg = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
    Row(
        modifier = modifier
            .clip(shape)
            .background(cardBg)
            .border(1.dp, colors.border.copy(alpha = 0.6f), shape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(colors.badgeBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
        }
        Column {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textMain, maxLines = 1)
            Text(label, fontSize = 12.sp, color = colors.textSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DexAppTile(
    app: AppMetadata,
    colors: DexWebColors,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val cardBg = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
    Column(
        modifier = modifier
            .clip(shape)
            .background(cardBg)
            .border(1.dp, colors.border.copy(alpha = 0.6f), shape)
            .clickable(onClick = onOpen)
            .padding(vertical = 20.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(app.color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(app.icon, contentDescription = app.title, tint = app.color, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = app.title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textMain,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
