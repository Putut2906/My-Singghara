package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.MainViewModel
import com.example.ui.models.AppRegistry

/**
 * Palet Web Connect (LocalHttpServer :root / body.dark) supaya tampilan DeX
 * menyerupai halaman Web Connect Akses PC 100% Offline.
 */
class DexWebColors(val dark: Boolean) {
    val sidebarBg = if (dark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val navbarBg = if (dark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val bgMain = if (dark) Color(0xFF0F172A) else Color(0xFFFFFFFF)
    val hover = if (dark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val border = if (dark) Color(0xFF334155) else Color(0xFFCBD5E1)
    val textMain = if (dark) Color(0xFFFFFFFF) else Color(0xFF141414)
    val textSub = if (dark) Color(0xFFCBD5E1) else Color(0xFF475569)
    val accent = if (dark) Color(0xFF38BDF8) else Color(0xFF3674B5)
    val badgeBg = if (dark) Color(0x3338BDF8) else Color(0x1F3674B5)
    val success = if (dark) Color(0xFF4ADE80) else Color(0xFF198754)
    val danger = if (dark) Color(0xFFF87171) else Color(0xFFE74C3C)
}

@Composable
fun rememberDexWebColors(): DexWebColors {
    val dark = isSystemInDarkTheme()
    return remember(dark) { DexWebColors(dark) }
}

private data class DexNavItem(
    val label: String,
    val icon: ImageVector,
    val iconColor: Color?,
    val selected: Boolean,
    val onClick: () -> Unit
)

private data class DexNavGroup(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val items: List<DexNavItem>
)

/** Brand kiri navbar web: logo + "My Singghara" + subjudul, dipakai pada top bar DeX. */
@Composable
fun DexWebTopBarBrand(colors: DexWebColors, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = "Logo My Singghara",
            modifier = Modifier.size(26.dp).clip(CircleShape)
        )
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "My Singghara",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textMain,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .background(colors.success.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .border(1.dp, colors.success.copy(alpha = 0.25f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "DESKTOP",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.success,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Text(
                text = "Samsung DeX \u2022 Akses Desktop 100% Offline",
                fontSize = 10.5.sp,
                color = colors.textSub,
                maxLines = 1
            )
        }
    }
}

/**
 * Sidebar ala Web Connect untuk mode DeX: lebar 250dp saat terbuka dan rail ikon
 * 72dp saat diciutkan (perilaku default web). Grup menu, warna, item aktif bergaris
 * kiri, serta footer profil mengikuti desain sidebar Web Connect.
 */
@Composable
fun DexWebSidebar(
    viewModel: MainViewModel,
    expanded: Boolean,
    onRequestExpand: () -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = rememberDexWebColors()
    var openGroupId by remember { mutableStateOf<String?>(null) }
    val currentRoute = viewModel.currentRoute
    val isLoggedIn = viewModel.currentFirebaseUser != null

    fun go(route: String) {
        viewModel.currentRoute = route
    }

    val myAppItems = buildList {
        add(
            DexNavItem(
                label = "Semua Aplikasi",
                icon = Icons.Default.GridOn,
                iconColor = Color(0xFFF59E0B),
                selected = currentRoute == "home" && viewModel.showAllAppsPage,
                onClick = {
                    viewModel.currentRoute = "home"
                    viewModel.showAllAppsPage = true
                }
            )
        )
        viewModel.recentAppIds.take(6)
            .mapNotNull { AppRegistry.allAppsMap[it] }
            .forEach { meta ->
                val route = meta.route ?: return@forEach
                add(
                    DexNavItem(
                        label = meta.title,
                        icon = meta.icon,
                        iconColor = meta.color,
                        selected = currentRoute == route,
                        onClick = { go(route) }
                    )
                )
            }
    }

    val groups = listOf(
        DexNavGroup(
            "my-apps", "My Aplikasi", Icons.Default.DashboardCustomize, Color(0xFFF59E0B),
            myAppItems
        ),
        DexNavGroup(
            "media", "Media", Icons.Default.VideoLibrary, Color(0xFF0E7490),
            listOf(
                DexNavItem("Pemutar Video", Icons.Default.VideoLibrary, Color(0xFF0E7490), currentRoute == "pemutar_video") { go("pemutar_video") },
                DexNavItem("Handy Talky (HT)", Icons.Default.SettingsVoice, Color(0xFF10B981), currentRoute == "handy_talky") { go("handy_talky") },
                DexNavItem("Musik", Icons.Default.MusicNote, Color(0xFFE91E63), currentRoute == "pemutar_musik") { go("pemutar_musik") }
            )
        ),
        DexNavGroup(
            "people", "People", Icons.Default.Groups, Color(0xFFF59E0B),
            listOf(
                DexNavItem("Arisan Keluarga", Icons.Default.Groups, Color(0xFF3674B5), currentRoute == "arisan_keluarga") { go("arisan_keluarga") },
                DexNavItem("Rekap Belanja Pasar", Icons.Default.ShoppingCart, Color(0xFF10B981), currentRoute == "rekap_belanja_pasar") { go("rekap_belanja_pasar") },
                DexNavItem("Belanja Online", Icons.Default.ShoppingBag, null, currentRoute == "belanja_online") { go("belanja_online") },
                DexNavItem("Kalkulator", Icons.Default.Calculate, Color(0xFF0EA5E9), currentRoute == "kalkulator") { go("kalkulator") }
            )
        ),
        DexNavGroup(
            "company", "Company", Icons.Default.Badge, Color(0xFF0F766E),
            listOf(
                DexNavItem("Data", Icons.Default.Badge, Color(0xFF0F766E), currentRoute == "data_karyawan_murid" || currentRoute == "tabel_anggota_lengkap") { go("data_karyawan_murid") },
                DexNavItem("Absensi", Icons.Default.DateRange, Color(0xFF0F766E), currentRoute == "absensi_karyawan" || currentRoute == "riwayat_absensi_lengkap") { go("absensi_karyawan") },
                DexNavItem("Gaji", Icons.Default.AccountBalanceWallet, Color(0xFF0F766E), currentRoute == "gaji_karyawan") { go("gaji_karyawan") }
            )
        ),
        DexNavGroup(
            "manajemen", "Manajemen Mandiri", Icons.Default.AccountBalanceWallet, colors.success,
            listOf(
                DexNavItem("Tabungan Mandiri", Icons.Default.AccountBalanceWallet, null, currentRoute == "tabungan") { go("tabungan") },
                DexNavItem("Daftar Belanja", Icons.Default.ShoppingCart, null, currentRoute == "belanja") { go("belanja") },
                DexNavItem("Catatan Penting", Icons.Default.Edit, null, currentRoute == "catatan") { go("catatan") },
                DexNavItem("Note Keuangan", Icons.Default.EditNote, null, currentRoute == "note_keuangan") { go("note_keuangan") }
            )
        ),
        DexNavGroup(
            "keuangan", "Laporan Keuangan", Icons.Default.AccountBalance, Color(0xFF0DCAF0),
            listOf(
                DexNavItem("Jurnal Umum", Icons.Default.List, Color(0xFF4CAF50), currentRoute == "jurnal_umum") { go("jurnal_umum") },
                DexNavItem("Buku Besar", Icons.Default.List, Color(0xFF2196F3), currentRoute == "buku_besar") { go("buku_besar") },
                DexNavItem("Neraca Saldo", Icons.Default.AccountBalance, Color(0xFFFF9800), currentRoute == "neraca_saldo") { go("neraca_saldo") },
                DexNavItem("Jurnal Penyesuaian", Icons.Default.Edit, Color(0xFF9C27B0), currentRoute == "jurnal_penyesuaian") { go("jurnal_penyesuaian") },
                DexNavItem("Neraca S. Disesuaikan", Icons.Default.List, Color(0xFF00BCD4), currentRoute == "neraca_saldo_disesuaikan") { go("neraca_saldo_disesuaikan") },
                DexNavItem("Laba Rugi", Icons.Default.Search, Color(0xFFE91E63), currentRoute == "laba_rugi") { go("laba_rugi") },
                DexNavItem("Jurnal Penutup", Icons.Default.Info, Color(0xFF795548), currentRoute == "jurnal_penutup") { go("jurnal_penutup") },
                DexNavItem("Neraca S. Setelah Tutup", Icons.Default.AccountBalance, Color(0xFF607D8B), currentRoute == "neraca_saldo_setelah_tutup") { go("neraca_saldo_setelah_tutup") }
            )
        ),
        DexNavGroup(
            "koneksi", "Koneksi dan Berbagi", Icons.Default.Share, Color(0xFF0284C7),
            buildList {
                add(DexNavItem("Share", Icons.Default.Share, Color(0xFF3674B5), currentRoute == "share") { go("share") })
                add(DexNavItem("Transfer File", Icons.Default.SwapHoriz, Color(0xFF0284C7), currentRoute == "transfer_file") { go("transfer_file") })
                if (!com.example.firebase.AdminUidStore.isConfigured() ||
                    com.example.firebase.AdminUidStore.isAdmin(viewModel.currentFirebaseUser?.uid)
                ) {
                    add(DexNavItem("Manajemen Akun", Icons.Default.AdminPanelSettings, Color(0xFF047857), currentRoute == "manajemen_akun") { go("manajemen_akun") })
                }
            }
        )
    )

    LaunchedEffect(currentRoute, viewModel.showAllAppsPage, expanded) {
        if (expanded) {
            val activeGroup = groups.firstOrNull { g -> g.items.any { it.selected } }
            if (activeGroup != null) openGroupId = activeGroup.id
        }
    }

    Column(
        modifier = modifier
            .width(if (expanded) 250.dp else 72.dp)
            .fillMaxHeight()
            .background(colors.sidebarBg)
    ) {
        if (expanded) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
            ) {
                DexNavItemRow(
                    DexNavItem(
                        label = "Dashboard",
                        icon = Icons.Default.Dashboard,
                        iconColor = null,
                        selected = currentRoute == "home" && !viewModel.showAllAppsPage,
                        onClick = {
                            viewModel.showAllAppsPage = false
                            go("home")
                        }
                    ),
                    colors
                )
                DexSidebarDivider(colors)
                groups.forEachIndexed { index, group ->
                    val isOpen = openGroupId == group.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { openGroupId = if (isOpen) null else group.id }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = group.icon,
                                contentDescription = group.label,
                                tint = group.color,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = group.label.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textMain,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Icon(
                            imageVector = if (isOpen) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                            contentDescription = if (isOpen) "Tutup grup" else "Buka grup",
                            tint = colors.textSub,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = isOpen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                            group.items.forEach { item -> DexNavItemRow(item, colors) }
                        }
                    }
                    if (index < groups.lastIndex) DexSidebarDivider(colors)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DexRailIcon(
                    icon = Icons.Default.Dashboard,
                    tint = if (currentRoute == "home" && !viewModel.showAllAppsPage) colors.accent else colors.textSub,
                    active = currentRoute == "home" && !viewModel.showAllAppsPage,
                    contentDescription = "Dashboard",
                    colors = colors
                ) {
                    viewModel.showAllAppsPage = false
                    go("home")
                }
                DexSidebarDivider(colors)
                groups.forEach { group ->
                    DexRailIcon(
                        icon = group.icon,
                        tint = group.color,
                        active = group.items.any { it.selected },
                        contentDescription = group.label,
                        colors = colors
                    ) {
                        openGroupId = group.id
                        onRequestExpand()
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { go("profil") }
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppUserAvatar(
                        name = viewModel.profileName,
                        imageUri = viewModel.profileImageUri,
                        avatarIndex = viewModel.profileAvatarIndex,
                        size = 36.dp,
                        textSize = 13.sp,
                        isLoggedIn = isLoggedIn,
                        contentDescription = "Profil Pengguna"
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = viewModel.profileName.ifEmpty { "Pengguna" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(if (isLoggedIn) colors.success else colors.textSub, CircleShape)
                            )
                            Text(
                                text = if (isLoggedIn) "Terhubung" else "Belum Masuk",
                                fontSize = 10.sp,
                                color = colors.textSub,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (isLoggedIn) {
                    DexFooterIconButton(Icons.Default.Logout, "Keluar", colors) {
                        viewModel.requestLogoutConfirmation()
                    }
                } else {
                    DexFooterIconButton(Icons.Default.Login, "Masuk", colors, onLogin)
                    DexFooterIconButton(Icons.Default.PersonAdd, "Daftar", colors, onRegister)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                AppUserAvatar(
                    name = viewModel.profileName,
                    imageUri = viewModel.profileImageUri,
                    avatarIndex = viewModel.profileAvatarIndex,
                    size = 36.dp,
                    textSize = 13.sp,
                    isLoggedIn = isLoggedIn,
                    contentDescription = "Profil Pengguna",
                    onClick = { go("profil") }
                )
            }
        }
    }
}

@Composable
private fun DexSidebarDivider(colors: DexWebColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(1.dp)
            .background(colors.border.copy(alpha = 0.5f))
    )
}

@Composable
private fun DexNavItemRow(item: DexNavItem, colors: DexWebColors) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(if (item.selected) colors.badgeBg else Color.Transparent)
            .clickable { item.onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = item.iconColor ?: if (item.selected) colors.accent else colors.textSub,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = item.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (item.selected) colors.accent else colors.textSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(colors.accent)
            )
        }
    }
}

@Composable
private fun DexRailIcon(
    icon: ImageVector,
    tint: Color,
    active: Boolean,
    contentDescription: String,
    colors: DexWebColors,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) colors.badgeBg else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun DexFooterIconButton(
    icon: ImageVector,
    contentDescription: String,
    colors: DexWebColors,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.textSub,
            modifier = Modifier.size(18.dp)
        )
    }
}