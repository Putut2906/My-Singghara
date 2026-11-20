package com.example.ui.components

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.firebase.AdminUidStore
import com.example.ui.MainViewModel
import com.example.ui.models.AppRegistry
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Palet shell DeX: identitas flat Material3 yang diturunkan langsung dari
 * MaterialTheme.colorScheme (bukan mirror Web Connect), sehingga otomatis ikut
 * light/dark dan kohesif dengan layar fitur. Tanpa bayangan/gradien/emoji.
 * Nama properti lama dipertahankan agar call site di MainActivity tetap valid.
 */
class DexWebColors(private val scheme: ColorScheme, val dark: Boolean) {
    val sidebarBg = scheme.surface
    val navbarBg = scheme.surface
    val bgMain = scheme.background
    val hover = scheme.surfaceVariant
    val border = scheme.outlineVariant
    val textMain = scheme.onSurface
    val textSub = scheme.onSurfaceVariant
    val accent = scheme.primary
    val badgeBg = scheme.secondaryContainer
    val success = scheme.primary
    val danger = scheme.error

    // Token gaya baru (flat Material3)
    val selectedBg = scheme.secondaryContainer
    val onSelectedBg = scheme.onSecondaryContainer
    val onHover = scheme.onSurfaceVariant
    val cardBg = scheme.surface
}

@Composable
fun rememberDexWebColors(): DexWebColors {
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    return remember(scheme, dark) { DexWebColors(scheme, dark) }
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

/** Brand kiri navbar DeX: logo + nama + caption minimalis (identitas flat M3). */
@Composable
fun DexWebTopBarBrand(colors: DexWebColors, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = "Logo My Singghara",
            modifier = Modifier.size(28.dp).clip(CircleShape)
        )
        Column {
            Text(
                text = "My Singghara",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textMain,
                maxLines = 1
            )
            Text(
                text = "Samsung DeX",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSub,
                maxLines = 1
            )
        }
    }
}

/**
 * Kontrol jendela mengambang (window controls) khusus mode DeX, di pojok kanan-atas.
 * Sepenuhnya tersembunyi: muncul saat pointer masuk zona sudut dan hilang saat pointer
 * keluar. Tombol: Minimalkan (moveTaskToBack), Maksimalkan/Pulihkan (sembunyikan/tampilkan
 * system bar immersive), Tutup (finish). Aksi memakai Activity asli, bukan context berskala DeX.
 */
@Composable
fun DexWindowControls(
    activity: ComponentActivity,
    colors: DexWebColors,
    modifier: Modifier = Modifier
) {
    var revealed by remember { mutableStateOf(false) }
    var isMaximized by remember { mutableStateOf(false) }

    fun toggleMaximize() {
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isMaximized) {
            controller.show(WindowInsetsCompat.Type.systemBars())
            isMaximized = false
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            isMaximized = true
        }
    }

    // Zona sudut tak terlihat yang menangkap hover pointer; tombol muncul di dalamnya.
    Box(
        modifier = modifier
            .width(320.dp)
            .height(104.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        when (awaitPointerEvent(PointerEventPass.Initial).type) {
                            PointerEventType.Enter -> revealed = true
                            PointerEventType.Exit -> revealed = false
                            else -> Unit
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopEnd
    ) {
        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.padding(top = 8.dp, end = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.navbarBg)
                    .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DexWindowControlButton(
                    icon = Icons.Default.Minimize,
                    description = "Minimalkan",
                    tint = colors.textMain,
                    onClick = { activity.moveTaskToBack(true) }
                )
                DexWindowControlButton(
                    icon = if (isMaximized) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                    description = if (isMaximized) "Pulihkan" else "Maksimalkan",
                    tint = colors.textMain,
                    onClick = { toggleMaximize() }
                )
                DexWindowControlButton(
                    icon = Icons.Default.Close,
                    description = "Tutup",
                    tint = colors.danger,
                    onClick = { activity.finish() }
                )
            }
        }
    }
}

@Composable
private fun DexWindowControlButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
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
    val context = LocalContext.current
    var openGroupId by remember { mutableStateOf<String?>(null) }
    val currentRoute = viewModel.currentRoute
    val isLoggedIn = viewModel.currentFirebaseUser != null
    val adminUids by AdminUidStore.adminUids.collectAsState()
    val isUserAdmin = AdminUidStore.isAdminIn(adminUids, viewModel.currentFirebaseUser?.uid)

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
                DexNavItem("Musik", Icons.Default.MusicNote, Color(0xFFE91E63), currentRoute == "pemutar_musik") { go("pemutar_musik") },
                DexNavItem("Stopwatch", Icons.Default.Timer, Color(0xFF7C3AED), currentRoute == "stopwatch") { go("stopwatch") }
            )
        ),
        DexNavGroup(
            "cat", "Simulasi CAT", Icons.Default.Schedule, Color(0xFF7C3AED),
            listOf(
                DexNavItem("Input Soal", Icons.Default.Edit, Color(0xFF7C3AED), currentRoute == "cat_input_soal") { go("cat_input_soal") },
                DexNavItem("Simulasi Ujian", Icons.Default.Timer, Color(0xFF7C3AED), currentRoute == "cat_simulasi_ujian") { go("cat_simulasi_ujian") },
                DexNavItem("Hasil & Live Score", Icons.Default.TrendingUp, Color(0xFF7C3AED), currentRoute == "cat_hasil") { go("cat_hasil") },
                DexNavItem("Grafik Progres", Icons.Default.ShowChart, Color(0xFF7C3AED), currentRoute == "cat_grafik_progres") { go("cat_grafik_progres") }
            )
        ),
        DexNavGroup(
            "j2me", "Game & Emulator", Icons.Default.Gamepad, Color(0xFF306230),
            listOf(
                DexNavItem("J2ME Loader", Icons.Default.Gamepad, Color(0xFF306230), false) {
                    try {
                        context.startActivity(Intent(context, ru.playsoftware.j2meloader.MainActivity::class.java))
                    } catch (_: Throwable) {
                    }
                },
                // Game bawaan dipindah ke sini dari halaman "My Aplikasi".
                // Dibuka lewat hub GameScreen (route "game_list") dengan mengaktifkan section-nya.
                DexNavItem(
                    "Puzzle Geser", Icons.Default.DashboardCustomize, Color(0xFFE65100),
                    currentRoute == "game_list" && viewModel.activeGameSectionInGameScreen == "sliding_puzzle"
                ) {
                    viewModel.currentRoute = "game_list"
                    viewModel.activeGameSectionInGameScreen = "sliding_puzzle"
                },
                DexNavItem(
                    "Block Puzzle", Icons.Default.GridOn, Color(0xFF2C5E6A),
                    currentRoute == "game_list" && viewModel.activeGameSectionInGameScreen == "block_puzzle"
                ) {
                    viewModel.currentRoute = "game_list"
                    viewModel.activeGameSectionInGameScreen = "block_puzzle"
                },
                DexNavItem(
                    "Brick Game", Icons.Default.Gamepad, Color(0xFF306230),
                    currentRoute == "game_list" && viewModel.activeGameSectionInGameScreen == "tetris"
                ) {
                    viewModel.currentRoute = "game_list"
                    viewModel.activeGameSectionInGameScreen = "tetris"
                }
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
                if (isUserAdmin) {
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
            // DeX fullscreen: jendela edge-to-edge, jadi footer sidebar perlu naik
            // di atas navigation bar. Diletak SETELAH background agar latar tetap
            // full-bleed sementara isi (footer profil) tidak terpotong.
            .navigationBarsPadding()
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
                                tint = colors.textSub,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = group.label.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSub,
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
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(if (item.selected) colors.selectedBg else Color.Transparent)
            .clickable { item.onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (item.selected) colors.onSelectedBg else colors.textSub,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = item.label,
                fontSize = 13.sp,
                fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (item.selected) colors.onSelectedBg else colors.textSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DexRailIcon(
    icon: ImageVector,
    active: Boolean,
    contentDescription: String,
    colors: DexWebColors,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) colors.selectedBg else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) colors.onSelectedBg else colors.textSub,
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
            .clip(RoundedCornerShape(12.dp))
            .background(colors.hover.copy(alpha = 0.6f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.onHover,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Judul + subjudul sebuah halaman untuk header chrome DeX (ala Web Connect). */
data class DexPageInfo(val title: String, val subtitle: String)

/**
 * Peta rute -> judul/subjudul halaman DeX. Rute di [EXCLUDED] tidak diberi chrome
 * karena punya tata letak khusus (dashboard, layar fullscreen/immersive).
 */
object DexPageChromeMeta {
    private val EXCLUDED = setOf(
        "home", "pemutar_video", "immersive_file_viewer", "handy_talky", "quran"
    )

    private val PAGES = mapOf(
        "catatan" to DexPageInfo("Catatan Penting", "Kelola catatan pribadi Anda"),
        "note_keuangan" to DexPageInfo("Note Keuangan", "Catatan pemasukan & pengeluaran"),
        "agenda" to DexPageInfo("Agenda", "Jadwal dan agenda harian"),
        "jadwal_harian" to DexPageInfo("Jadwal Harian", "Rencana aktivitas harian"),
        "kalender" to DexPageInfo("Kalender", "Tampilan bulanan agenda & jadwal"),
        "belanja" to DexPageInfo("Daftar Belanja", "Pantau kebutuhan belanja"),
        "semua_transaksi_belanja" to DexPageInfo("Semua Transaksi Belanja", "Riwayat transaksi belanja"),
        "rekap_belanja_pasar" to DexPageInfo("Rekap Belanja Pasar", "Rekapitulasi belanja pasar"),
        "belanja_online" to DexPageInfo("Belanja Online", "Catat pembelian daring"),
        "tabungan" to DexPageInfo("Tabungan Mandiri", "Kelola pos tabungan"),
        "semua_catatan_tabungan" to DexPageInfo("Semua Catatan Tabungan", "Riwayat setoran & penarikan"),
        "arisan_keluarga" to DexPageInfo("Arisan Keluarga", "Pengelolaan arisan"),
        "my_kasir" to DexPageInfo("My Kasir", "Point of sale sederhana"),
        "pembukuan_usaha" to DexPageInfo("Pembukuan Usaha", "Buku kas & aktivitas usaha"),
        "gaji_karyawan" to DexPageInfo("Gaji Karyawan", "Perhitungan & riwayat gaji"),
        "absensi_karyawan" to DexPageInfo("Absensi Karyawan", "Presensi harian"),
        "riwayat_absensi_lengkap" to DexPageInfo("Riwayat Absensi", "Rekap kehadiran lengkap"),
        "data_karyawan_murid" to DexPageInfo("Data Karyawan & Murid", "Master data anggota"),
        "tabel_anggota_lengkap" to DexPageInfo("Anggota Lengkap", "Tabel seluruh anggota"),
        "jurnal_umum" to DexPageInfo("Jurnal Umum", "Pencatatan transaksi akuntansi"),
        "buku_besar" to DexPageInfo("Buku Besar", "Ringkasan per akun"),
        "neraca_saldo" to DexPageInfo("Neraca Saldo", "Daftar saldo akun"),
        "jurnal_penyesuaian" to DexPageInfo("Jurnal Penyesuaian", "Ayat penyesuaian akhir periode"),
        "neraca_saldo_disesuaikan" to DexPageInfo("Neraca Saldo Disesuaikan", "Saldo setelah penyesuaian"),
        "laba_rugi" to DexPageInfo("Laporan Laba Rugi", "Ringkasan pendapatan & beban"),
        "jurnal_penutup" to DexPageInfo("Jurnal Penutup", "Penutupan akun nominal"),
        "neraca_saldo_setelah_tutup" to DexPageInfo("Neraca Setelah Tutup", "Saldo awal periode berikutnya"),
        "pemutar_musik" to DexPageInfo("Pemutar Musik", "Pemutar audio & lirik"),
        "mindmap" to DexPageInfo("Mind Map", "Peta pikiran visual"),
        "stopwatch" to DexPageInfo("Stopwatch", "Penghitung waktu"),
        "kalkulator" to DexPageInfo("Kalkulator", "Alat hitung"),
        "game_list" to DexPageInfo("Game", "Kumpulan permainan"),
        "sliding_puzzle" to DexPageInfo("Sliding Puzzle", "Teka-teki geser"),
        "jigsaw_puzzle" to DexPageInfo("Jigsaw Puzzle", "Teka-teki susun"),
        "j2me_loader" to DexPageInfo("J2ME Loader", "Emulator game Java"),
        "share" to DexPageInfo("Share", "Bagikan konten antar perangkat"),
        "transfer_file" to DexPageInfo("Transfer File", "Kirim file antar perangkat"),
        "web_connect" to DexPageInfo("Web Connect", "Akses dasbor dari PC"),
        "telegram_ai" to DexPageInfo("Telegram AI", "Asisten berbasis Telegram"),
        "manajemen_akun" to DexPageInfo("Manajemen Akun", "Kelola pengguna & admin"),
        "profil" to DexPageInfo("Profil", "Informasi akun Anda"),
        "app_lock" to DexPageInfo("App Lock", "Kunci keamanan aplikasi"),
        "cat_input_soal" to DexPageInfo("Simulasi CAT - Input Soal", "Kelola bank soal"),
        "cat_simulasi_ujian" to DexPageInfo("Simulasi CAT - Ujian", "Laksanakan try out"),
        "cat_hasil" to DexPageInfo("Simulasi CAT - Hasil", "Rekap nilai try out"),
        "cat_grafik_progres" to DexPageInfo("Simulasi CAT - Grafik Progres", "Tren skor TWK / TIU / TKP"),
        "tentang_aplikasi" to DexPageInfo("Tentang Aplikasi", "Informasi My Singghara")
    )

    /** Returning null berarti rute ini tidak memakai page chrome. */
    fun forRoute(route: String): DexPageInfo? {
        if (route in EXCLUDED) return null
        PAGES[route]?.let { return it }
        val humanized = route.replace("_", " ").trim().replaceFirstChar { it.uppercase() }
        return DexPageInfo(humanized, "")
    }
}

/**
 * Page chrome ala Web Connect untuk mode DeX: header (judul + subjudul) lalu konten
 * dibungkus kartu ber-border. Layar Compose yang ada dipakai ulang di dalam [content],
 * sehingga semua halaman punya tata letak desktop yang konsisten tanpa dirombak satu-satu.
 */
@Composable
fun DexPageChrome(info: DexPageInfo, content: @Composable () -> Unit) {
    val colors = rememberDexWebColors()
    Column(modifier = Modifier.fillMaxSize().background(colors.bgMain)) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            Text(
                text = info.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (info.subtitle.isNotBlank()) {
                Text(
                    text = info.subtitle,
                    fontSize = 12.5.sp,
                    color = colors.textSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBg)
        ) {
            content()
        }
    }
}