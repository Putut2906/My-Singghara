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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.models.AppMetadata
import com.example.ui.models.AppRegistry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Dashboard native untuk Samsung DeX: meniru tata letak halaman Web Connect PC
 * (banner hero sambutan bergradien + kartu "My Aplikasi" dengan ikon squircle +
 * baris kartu statistik) memakai UI/UX My Singghara sendiri.
 * Murni Compose, tanpa WebView/localhost. Navigasi memakai viewModel.currentRoute.
 */
@Composable
fun DexDashboardScreen(viewModel: MainViewModel) {
    val colors = rememberDexWebColors()
    val notes by viewModel.notes.collectAsState()
    val agendas by viewModel.agendaItems.collectAsState()
    val shopping by viewModel.shoppingItems.collectAsState()

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val (greeting, greetingIcon) = remember(hour) {
        when (hour) {
            in 5..10 -> "Selamat Pagi" to Icons.Default.WbSunny
            in 11..14 -> "Selamat Siang" to Icons.Default.SettingsBrightness
            in 15..17 -> "Selamat Sore" to Icons.Default.WbTwilight
            else -> "Selamat Malam" to Icons.Default.Bedtime
        }
    }

    val today = remember {
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(Date())
    }

    val apps = remember { AppRegistry.defaultOrder.mapNotNull { AppRegistry.allAppsMap[it] } }
    val columnsPerRow = 6

    val stats = listOf(
        Triple("Catatan", notes.size.toString(), Icons.Default.EditNote, Color(0xFF6366F1)),
        Triple("Agenda", agendas.size.toString(), Icons.Default.DateRange, Color(0xFFF59E0B)),
        Triple("Item Belanja", shopping.size.toString(), Icons.Default.ShoppingCart, Color(0xFF10B981)),
        Triple("Aplikasi", AppRegistry.allAppsMap.size.toString(), Icons.Default.GridOn, Color(0xFF0EA5E9))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgMain)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // SECTION 1: Banner hero sambutan (gradien teal, paritas Web Connect)
        val heroShape = RoundedCornerShape(24.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0F766E), Color(0xFF14B8A6), Color(0xFF06B6D4))
                    )
                )
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(greetingIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Column {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(greeting, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = viewModel.profileName.ifEmpty { "Pengguna" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(today, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // SECTION 2: Kartu "My Aplikasi"
        DexCard(colors = colors) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("My Aplikasi", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(1.dp, colors.border, CircleShape)
                        .clickable {
                            viewModel.currentRoute = "home"
                            viewModel.showAllAppsPage = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Lihat Semua Aplikasi", tint = colors.accent, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            apps.chunked(columnsPerRow).forEach { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
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

        Spacer(Modifier.height(20.dp))

        // SECTION 3: Baris kartu statistik
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            stats.forEach { (label, value, icon, iconBg) ->
                DexStatCard(label = label, value = value, icon = icon, iconBg = iconBg, colors = colors, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Kontainer kartu ala Web Connect: radius besar, border tipis, latar kartu. */
@Composable
private fun DexCard(
    colors: DexWebColors,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val cardBg = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
            .border(1.dp, colors.border.copy(alpha = 0.6f), shape)
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun DexAppTile(
    app: AppMetadata,
    colors: DexWebColors,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onOpen).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(app.color),
            contentAlignment = Alignment.Center
        ) {
            Icon(app.icon, contentDescription = app.title, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = app.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DexStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    iconBg: Color,
    colors: DexWebColors,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val cardBg = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
    Row(
        modifier = modifier
            .clip(shape)
            .background(cardBg)
            .border(1.dp, colors.border.copy(alpha = 0.6f), shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 11.5.sp, color = colors.textSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
