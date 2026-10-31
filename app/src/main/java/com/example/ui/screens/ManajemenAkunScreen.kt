package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.firebase.CloudMessageDraft
import com.example.firebase.CloudMessageItem
import com.example.firebase.CloudUserItem
import com.example.firebase.FirebaseAccountAdminManager
import com.example.ui.MainViewModel
import com.example.ui.components.AdminPhoneNumberInputField
import com.example.ui.components.AppUserAvatar
import com.example.ui.components.OfflineAnimatedState
import com.example.ui.components.VipCardSide
import com.example.ui.components.VipMembershipCard
import com.example.ui.components.VipMembershipDetailDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layar Manajemen Akun & Pesan (Khusus Online & Khusus Administrator)
 * UID Admin Resmi: UiDpHxawlBQ17mxTUHIXvhxRR3b2
 *
 * Fitur:
 * 1. Pengecekan Online: Jika internet terputus, menampilkan animasi bergerak interaktif.
 * 2. Pengecekan Hak Akses Admin: Hanya UID 'UiDpHxawlBQ17mxTUHIXvhxRR3b2' yang dapat masuk.
 * 3. Sub Menu 1: Daftar Pengguna (Menampilkan seluruh akun dan token FCM otomatis).
 * 4. Sub Menu 2: Pesan (Menulis dan menghapus pesan khusus admin ke koleksi messages).
 * 5. Dialog Aturan Keamanan Firestore Rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManajemenAkunScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenAuthDialog: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adminManager = remember { FirebaseAccountAdminManager.getInstance() }

    // Status Jaringan Online
    val isOnline = viewModel.isNetworkAvailable
    var isCheckingNetwork by remember { mutableStateOf(false) }

    // Status Admin UID
    val currentUser = viewModel.currentFirebaseUser
    val isCurrentAdmin = remember(currentUser) {
        adminManager.isAdmin(currentUser?.uid)
    }

    // Sub Menu: 0 = Daftar Pengguna, 1 = Pesan (Diakses via menu titik 3 di samping icon refresh)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Data State
    var isLoadingUsers by remember { mutableStateOf(false) }
    var userList by remember { mutableStateOf<List<CloudUserItem>>(emptyList()) }
    var userSearchQuery by remember { mutableStateOf("") }

    var isLoadingMessages by remember { mutableStateOf(false) }
    var messageList by remember { mutableStateOf<List<CloudMessageItem>>(emptyList()) }

    // BackHandler: Kembali menggunakan tombol fisik / gesture Back OS Android (menggantikan TopAppBar lokal dengan icon <-)
    BackHandler(enabled = true) {
        onBack()
    }

    // Dialog States
    var showWriteMessageDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showOtpInboxDialog by remember { mutableStateOf(false) }
    val otpRequestsList by viewModel.otpRequests.collectAsState()
    var messageToDelete by remember { mutableStateOf<CloudMessageItem?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var initialMessageTarget by remember { mutableStateOf<CloudUserItem?>(null) }

    // State untuk Mengaktifkan / Menonaktifkan Akun Pengguna (Khusus Admin)
    var userToToggleStatus by remember { mutableStateOf<CloudUserItem?>(null) }
    var isTogglingUserStatus by remember { mutableStateOf(false) }

    // State untuk Mengatur Membership Pengguna (1, 3, 5, 7, 12 Bulan)
    var userToManageMembership by remember { mutableStateOf<CloudUserItem?>(null) }
    var isApplyingMembership by remember { mutableStateOf(false) }

    // State untuk Menampilkan Popup Detail Membership Pengguna (Kartu VIP)
    var userForMembershipDetail by remember { mutableStateOf<CloudUserItem?>(null) }

    // Muat data saat online dan berstatus admin
    fun loadAllAdminData() {
        if (!isOnline || !isCurrentAdmin) return
        viewModel.refreshOtpRequests()
        scope.launch {
            isLoadingUsers = true
            val usersRes = adminManager.getAllUsers()
            if (usersRes.isSuccess) {
                userList = usersRes.getOrDefault(emptyList())
            } else {
                viewModel.showToastPopup("Gagal memuat pengguna: ${usersRes.exceptionOrNull()?.message}")
            }
            isLoadingUsers = false

            isLoadingMessages = true
            val msgRes = adminManager.getAllMessages()
            if (msgRes.isSuccess) {
                messageList = msgRes.getOrDefault(emptyList())
            } else {
                viewModel.showToastPopup("Gagal memuat pesan: ${msgRes.exceptionOrNull()?.message}")
            }
            isLoadingMessages = false
        }
    }

    // Fungsi Toggle Status Aktif/Nonaktif Akun Pengguna
    fun toggleUserStatus(targetUser: CloudUserItem) {
        if (!isCurrentAdmin) return
        scope.launch {
            isTogglingUserStatus = true
            val newStatus = !targetUser.isActive
            val res = adminManager.setUserActiveStatus(targetUser.uid, newStatus)
            isTogglingUserStatus = false
            userToToggleStatus = null
            if (res.isSuccess) {
                val statusText = if (newStatus) "diaktifkan kembali" else "dinonaktifkan"
                viewModel.showToastPopup("Akun ${targetUser.displayName} berhasil $statusText!")
                loadAllAdminData()
            } else {
                viewModel.showToastPopup("Gagal mengubah status akun: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    // Fungsi Atur Masa Membership Pengguna (1, 3, 5, 7, atau 12 Bulan)
    fun applyMembership(targetUser: CloudUserItem, months: Int, activateAccount: Boolean) {
        if (!isCurrentAdmin) return
        scope.launch {
            isApplyingMembership = true
            val res = adminManager.setUserMembership(targetUser.uid, months, activateAccount)
            isApplyingMembership = false
            userToManageMembership = null
            if (res.isSuccess) {
                val tierText = if (months > 0) "$months Bulan" else "Reset (Bebas)"
                viewModel.showToastPopup("Membership $tierText berhasil diterapkan untuk ${targetUser.displayName}!")
                loadAllAdminData()
            } else {
                viewModel.showToastPopup("Gagal mengatur membership: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    LaunchedEffect(isOnline, isCurrentAdmin) {
        if (isOnline && isCurrentAdmin) {
            loadAllAdminData()
        }
    }

    // Helper Copy to Clipboard
    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        viewModel.showToastPopup("$label berhasil disalin!")
    }

    // KASUS 1: INTERNET TERPUTUS (Tampilkan Animasi Bergerak Khusus)
    if (!isOnline) {
        OfflineAnimatedState(
            title = "Koneksi Internet Terputus",
            description = "Menu Manajemen Akun adalah fitur khusus online. Diperlukan sambungan internet aktif untuk memverifikasi hak Administrator (UID: ${FirebaseAccountAdminManager.ADMIN_UID}) dan menyinkronkan data cloud Firebase.",
            isChecking = isCheckingNetwork,
            onRetry = {
                scope.launch {
                    isCheckingNetwork = true
                    viewModel.refreshNetworkStatus()
                    kotlinx.coroutines.delay(1200)
                    isCheckingNetwork = false
                    if (viewModel.isNetworkAvailable) {
                        viewModel.showToastPopup("Koneksi internet terhubung kembali!")
                    } else {
                        viewModel.showToastPopup("Internet masih belum terdeteksi. Silakan periksa WiFi / Data.")
                    }
                }
            },
            onBack = onBack
        )
        return
    }

    // KASUS 2: PENGGUNA BUKAN ADMIN (Akses Ditolak)
    if (!isCurrentAdmin) {
        Scaffold { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF2F2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Terkunci",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Akses Khusus Administrator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Menu Manajemen Akun hanya dapat diakses oleh akun Administrator resmi yang terdaftar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Box info UID Admin yang dibutuhkan
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "UID Admin Resmi Diperlukan:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            SelectionContainer {
                                Text(
                                    text = FirebaseAccountAdminManager.ADMIN_UID,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "UID Akun Anda Saat Ini:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            SelectionContainer {
                                Text(
                                    text = currentUser?.uid ?: "Belum Masuk / Guest",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Normal,
                                    color = if (currentUser == null) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onOpenAuthDialog,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (currentUser == null) "Masuk dengan Akun Admin" else "Beralih ke Akun Admin",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Kembali ke Beranda")
                        }
                    }
                }
            }
        }
        return
    }

    // KASUS 3: PENGGUNA ADALAH ADMIN RESMI (Tampilkan Dashboard Manajemen Penuh)
    // Navigasi kembali ditangani oleh tombol back pada OS Android via BackHandler.
    Scaffold { paddingValues ->
        val isDark = isSystemInDarkTheme()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // TOP ACTION BAR LOKAL MANAJEMEN AKUN
            // Sesuai permintaan pengguna: Kembalikan icon titik 3 dan icon yang berisi aturan rules firestore ke sebelah kanan icon refresh.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Judul Menu & Title "ADMIN" (satu baris utuh dengan warna adaptif mode terang dan gelap)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "Manajemen Akun",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7))
                            .border(1.dp, if (isDark) Color(0xFF059669) else Color(0xFF86EFAC), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ADMIN",
                            color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Action Bar: Input Nomor Admin (Default: 088806643415, bisa diganti) & Icon Email {pesan masuk kode} di sebelah kiri icon titik 3
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Input Nomor Default Admin 088806643415 (Bisa diganti) di sebelah kiri icon titik 3
                    AdminPhoneNumberInputField(
                        phoneNumber = viewModel.adminPhoneNumber,
                        onPhoneNumberChange = { newNumber ->
                            viewModel.updateAdminPhoneNumber(newNumber)
                        }
                    )

                    // Icon Email {Pesan Masuk Kode} diletakkan di sebelah kiri icon titik 3 (Khusus Admin)
                    val activeOtpCount = otpRequestsList.count { it.status == "MENUNGGU" && !it.isExpired }
                    Box {
                        BadgedBox(
                            badge = {
                                if (activeOtpCount > 0) {
                                    Badge(
                                        containerColor = Color(0xFFDC2626),
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = if (activeOtpCount > 99) "99+" else activeOtpCount.toString(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.refreshOtpRequests()
                                    showOtpInboxDialog = true
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("admin_email_otp_inbox_button")
                            ) {
                                Icon(
                                    imageVector = if (activeOtpCount > 0) Icons.Default.MarkEmailUnread else Icons.Default.Email,
                                    contentDescription = "Pesan Masuk Kode OTP Sandi Email",
                                    tint = if (activeOtpCount > 0) Color(0xFF0F766E) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Action Bar: Icon Titik 3 (Menu Overflow)
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu Titik Tiga",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Pesan Masuk Kode OTP")
                                    if (activeOtpCount > 0) {
                                        Badge(
                                            containerColor = Color(0xFFDC2626),
                                            contentColor = Color.White
                                        ) {
                                            Text(activeOtpCount.toString(), fontSize = 10.sp)
                                        }
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color(0xFF0F766E)
                                )
                            },
                            onClick = {
                                viewModel.refreshOtpRequests()
                                showOtpInboxDialog = true
                                showOverflowMenu = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Daftar Pengguna") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.People,
                                    contentDescription = null,
                                    tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                selectedTab = 0
                                showOverflowMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Pesan Pengumuman") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Campaign,
                                    contentDescription = null,
                                    tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                selectedTab = 1
                                showOverflowMenu = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Tulis Pesan") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PostAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                initialMessageTarget = null
                                showWriteMessageDialog = true
                                showOverflowMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Muat Ulang Data") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                loadAllAdminData()
                                showOverflowMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Aturan Rules Firestore") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                                )
                            },
                            onClick = {
                                showRulesDialog = true
                                showOverflowMenu = false
                            }
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // KONTEN SUB MENU SESUAI TAB TERPILIH
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> {
                        // SUB MENU 1: DAFTAR PENGGUNA
                        DaftarPenggunaContent(
                            userList = userList,
                            messageList = messageList,
                            isLoading = isLoadingUsers,
                            searchQuery = userSearchQuery,
                            onSearchQueryChange = { userSearchQuery = it },
                            onCopyUid = { copyToClipboard("UID Pengguna", it) },
                            onCopyToken = { copyToClipboard("Token FCM", it) },
                            onSendMessageToUser = { targetUser ->
                                initialMessageTarget = targetUser
                                showWriteMessageDialog = true
                            },
                            onToggleUserStatus = { targetUser ->
                                userToToggleStatus = targetUser
                            },
                            onManageMembership = { targetUser ->
                                userToManageMembership = targetUser
                            },
                            onOpenMembershipDetail = { targetUser ->
                                userForMembershipDetail = targetUser
                            },
                            onOpenPesan = { selectedTab = 1 }
                        )
                    }
                    1 -> {
                        // SUB MENU 2: PESAN (HANYA ADMIN YANG DAPAT MENULIS DAN HAPUS PESAN)
                        PesanAdminContent(
                            messageList = messageList,
                            isLoading = isLoadingMessages,
                            onWriteMessageClick = {
                                initialMessageTarget = null
                                showWriteMessageDialog = true
                            },
                            onDeleteMessageClick = { message ->
                                messageToDelete = message
                            },
                            onDeleteAllClick = {
                                showDeleteAllDialog = true
                            },
                            onBackToUsers = { selectedTab = 0 }
                        )
                    }
                }
            }
        }
    }

    // DIALOG TULIS PESAN SIARAN (KHUSUS ADMIN)
    if (showWriteMessageDialog) {
        WriteMessageDialog(
            targetUser = initialMessageTarget,
            onDismiss = {
                showWriteMessageDialog = false
                initialMessageTarget = null
            },
            onSubmitSingle = { title, content, category, priority, targetUid, targetName ->
                scope.launch {
                    val res = adminManager.createMessage(
                        title = title,
                        content = content,
                        category = category,
                        priority = priority,
                        target = targetUid,
                        targetName = targetName
                    )
                    if (res.isSuccess) {
                        viewModel.showToastPopup("Pesan berhasil dikirim dan tersimpan di koleksi messages!")
                        showWriteMessageDialog = false
                        initialMessageTarget = null
                        // Refresh daftar pesan
                        loadAllAdminData()
                    } else {
                        viewModel.showToastPopup("Gagal mengirim pesan: ${res.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }

    // DIALOG KONFIRMASI AKTIFKAN / NONAKTIFKAN AKUN PENGGUNA (KHUSUS ADMIN RESMI)
    userToToggleStatus?.let { targetUser ->
        val isDark = isSystemInDarkTheme()
        val willDeactivate = targetUser.isActive
        val accentColor = if (willDeactivate) MaterialTheme.colorScheme.error else (if (isDark) Color(0xFF34D399) else Color(0xFF15803D))
        val boxBgColor = if (willDeactivate) {
            if (isDark) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else Color(0xFFFEF2F2)
        } else {
            if (isDark) Color(0xFF064E3B).copy(alpha = 0.4f) else Color(0xFFF0FDF4)
        }
        val boxBorderColor = if (willDeactivate) {
            if (isDark) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else Color(0xFFFCA5A5)
        } else {
            if (isDark) Color(0xFF059669) else Color(0xFF86EFAC)
        }
        val boxTextColor = if (willDeactivate) {
            if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
        } else {
            if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D)
        }

        AlertDialog(
            onDismissRequest = { if (!isTogglingUserStatus) userToToggleStatus = null },
            icon = {
                Icon(
                    imageVector = if (willDeactivate) Icons.Default.Block else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(34.dp)
                )
            },
            title = {
                Text(
                    text = if (willDeactivate) "Nonaktifkan Akun Pengguna?" else "Aktifkan Kembali Akun?",
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (willDeactivate) {
                            "Apakah Anda yakin ingin menonaktifkan akun \"${targetUser.displayName}\"? Proteksi aktif: pengguna tidak akan bisa login, dan jika aplikasi dibuka akses akan langsung diblokir secara otomatis."
                        } else {
                            "Apakah Anda yakin ingin mengaktifkan kembali akun \"${targetUser.displayName}\"? Akun akan dapat kembali mengakses seluruh fitur aplikasi dan login normal."
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(boxBgColor)
                            .border(1.dp, boxBorderColor, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "INFORMASI AKUN TARGET",
                                color = boxTextColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "UID: ${targetUser.uid}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (targetUser.email.isNotEmpty()) {
                                Text(
                                    text = "Email: ${targetUser.email}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Akses Terakhir: ${targetUser.getFormattedLastActive()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tindakan: Akun akan ${if (willDeactivate) "DINONAKTIFKAN (BLOKIR AKSES & LOGIN)" else "DIAKTIFKAN KEMBALI"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = boxTextColor
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        toggleUserStatus(targetUser)
                    },
                    enabled = !isTogglingUserStatus,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (willDeactivate) MaterialTheme.colorScheme.error else (if (isDark) Color(0xFF059669) else Color(0xFF16A34A))
                    )
                ) {
                    if (isTogglingUserStatus) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(
                            imageVector = if (willDeactivate) Icons.Default.Block else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(if (willDeactivate) "Ya, Nonaktifkan" else "Ya, Aktifkan")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { userToToggleStatus = null },
                    enabled = !isTogglingUserStatus
                ) {
                    Text("Batal")
                }
            }
        )
    }

    // DIALOG ATUR MEMBERSHIP PENGGUNA (1, 3, 5, 7, 12 BULAN)
    userToManageMembership?.let { targetUser ->
        UserMembershipDialog(
            user = targetUser,
            isApplying = isApplyingMembership,
            adminPhoneNumber = viewModel.adminPhoneNumber,
            onDismiss = { userToManageMembership = null },
            onApplyMembership = { months, activateAccount ->
                applyMembership(targetUser, months, activateAccount)
            }
        )
    }

    // POPUP DETAIL MEMBERSHIP VIP (Muncul Saat Klik Profil User)
    userForMembershipDetail?.let { targetUser ->
        VipMembershipDetailDialog(
            user = targetUser,
            currentUserName = targetUser.displayName,
            currentUserEmail = targetUser.email,
            adminPhoneNumber = viewModel.adminPhoneNumber,
            isViewerAdmin = isCurrentAdmin,
            onDismiss = { userForMembershipDetail = null },
            onAdminManageMembership = { user ->
                userForMembershipDetail = null
                userToManageMembership = user
            }
        )
    }

    // DIALOG KONFIRMASI HAPUS PESAN SECARA PERMANEN (KHUSUS ADMIN TERDAFTAR)
    messageToDelete?.let { msg ->
        val isDark = isSystemInDarkTheme()
        val warnBoxBg = if (isDark) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else Color(0xFFFEF2F2)
        val warnBoxBorder = if (isDark) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else Color(0xFFFCA5A5)
        val warnBoxTitle = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
        val warnBoxText = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF7F1D1D)
        val warnBoxUid = if (isDark) Color(0xFFF87171) else Color(0xFF991B1B)

        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            icon = {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(34.dp))
            },
            title = { Text("Hapus Pesan Permanen?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Apakah Anda yakin ingin menghapus pesan \"${msg.title}\" secara permanen dari Cloud Firestore?",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(warnBoxBg)
                            .border(1.dp, warnBoxBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "HANYA ADMIN RESMI TERDAFTAR",
                                color = warnBoxTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Dokumen akan dihapus secara permanen dari koleksi Firestore 'messages'. Hanya UID berikut yang diizinkan:",
                                color = warnBoxText,
                                fontSize = 11.sp
                            )
                            SelectionContainer {
                                Text(
                                    text = FirebaseAccountAdminManager.ADMIN_UID,
                                    color = warnBoxUid,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = msg.id
                        messageToDelete = null
                        if (currentUser?.uid != FirebaseAccountAdminManager.ADMIN_UID) {
                            viewModel.showToastPopup("Akses Ditolak: Hanya UID ${FirebaseAccountAdminManager.ADMIN_UID} yang berwenang.")
                            return@Button
                        }
                        scope.launch {
                            val res = adminManager.deleteMessage(id)
                            if (res.isSuccess) {
                                viewModel.showToastPopup("Pesan berhasil dihapus secara permanen dari Firestore!")
                                loadAllAdminData()
                            } else {
                                viewModel.showToastPopup("Gagal menghapus pesan: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hapus Permanen", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // DIALOG KONFIRMASI HAPUS SELURUH PESAN SECARA PERMANEN (KHUSUS ADMIN TERDAFTAR)
    if (showDeleteAllDialog) {
        val isDark = isSystemInDarkTheme()
        val warnBoxBg = if (isDark) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else Color(0xFFFEF2F2)
        val warnBoxBorder = if (isDark) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else Color(0xFFFCA5A5)
        val warnBoxTitle = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
        val warnBoxText = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF7F1D1D)
        val warnBoxUid = if (isDark) Color(0xFFF87171) else Color(0xFF991B1B)

        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
            },
            title = { Text("Hapus Seluruh Pesan?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Apakah Anda yakin ingin menghapus seluruh ${messageList.size} pesan secara permanen dari Cloud Firestore?",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(warnBoxBg)
                            .border(1.dp, warnBoxBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "TINDAKAN PERMANEN TIDAK DAPAT DIBATALKAN",
                                color = warnBoxTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Seluruh dokumen pesan akan dihapus dari Firestore koleksi 'messages'. Hanya UID Administrator berikut yang berwenang:",
                                color = warnBoxText,
                                fontSize = 11.sp
                            )
                            SelectionContainer {
                                Text(
                                    text = FirebaseAccountAdminManager.ADMIN_UID,
                                    color = warnBoxUid,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        if (currentUser?.uid != FirebaseAccountAdminManager.ADMIN_UID) {
                            viewModel.showToastPopup("Akses Ditolak: Hanya UID ${FirebaseAccountAdminManager.ADMIN_UID} yang berwenang.")
                            return@Button
                        }
                        scope.launch {
                            val res = adminManager.deleteAllMessagesPermanently()
                            if (res.isSuccess) {
                                val count = res.getOrDefault(0)
                                viewModel.showToastPopup("$count pesan berhasil dihapus secara permanen dari Firestore!")
                                loadAllAdminData()
                            } else {
                                viewModel.showToastPopup("Gagal menghapus pesan: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hapus Semua Permanen", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // DIALOG ATURAN RULES FIRESTORE
    if (showRulesDialog) {
        FirestoreRulesDialog(
            onDismiss = { showRulesDialog = false },
            onCopyRules = {
                val rulesCode = adminManager.getFirestoreSecurityRulesCode()
                copyToClipboard("Aturan Rules Firestore", rulesCode)
            }
        )
    }

    // DIALOG PESAN MASUK KODE OTP (KHUSUS ADMIN)
    if (showOtpInboxDialog) {
        AdminOtpInboxDialog(
            otpList = otpRequestsList,
            onDismiss = { showOtpInboxDialog = false },
            onRefresh = { viewModel.refreshOtpRequests() },
            onDelete = { otpId -> viewModel.deleteAdminOtpRequest(otpId) },
            onMarkUsed = { otpId -> viewModel.markAdminOtpUsed(otpId) }
        )
    }
}

/**
 * Konten Sub Menu 1: Daftar Pengguna & Token FCM Otomatis
 */
@Composable
fun DaftarPenggunaContent(
    userList: List<CloudUserItem>,
    messageList: List<CloudMessageItem> = emptyList(),
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCopyUid: (String) -> Unit,
    onCopyToken: (String) -> Unit,
    onSendMessageToUser: (CloudUserItem) -> Unit,
    onToggleUserStatus: (CloudUserItem) -> Unit,
    onManageMembership: (CloudUserItem) -> Unit,
    onOpenMembershipDetail: (CloudUserItem) -> Unit = {},
    onOpenPesan: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    var statusFilter by remember { mutableStateOf("ALL") }

    val filteredList = remember(userList, searchQuery, statusFilter) {
        userList.filter { user ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                user.displayName.contains(searchQuery, ignoreCase = true) ||
                user.email.contains(searchQuery, ignoreCase = true) ||
                user.uid.contains(searchQuery, ignoreCase = true) ||
                user.deviceModel.contains(searchQuery, ignoreCase = true)
            }
            val matchesFilter = when (statusFilter) {
                "ACTIVE" -> user.isActive && !user.isMembershipExpired()
                "INACTIVE" -> !user.isActive || user.isMembershipExpired()
                "MEMBER" -> user.hasMembership()
                "ONLINE" -> user.isRecentlyActive()
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    val totalCount = userList.size
    val activeCount = remember(userList) { userList.count { it.isActive && !it.isMembershipExpired() } }
    val inactiveCount = remember(userList) { userList.count { !it.isActive || it.isMembershipExpired() } }
    val memberCount = remember(userList) { userList.count { it.hasMembership() } }
    val onlineCount = remember(userList) { userList.count { it.isRecentlyActive() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Kolom Pencarian dengan Tombol Refresh Langsung
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cari nama, email, UID, atau perangkat...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Hapus")
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Filter Chips Bar + Tombol Akses Pesan Admin
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = statusFilter == "ALL",
                onClick = { statusFilter = "ALL" },
                label = { Text("Semua ($totalCount)", fontSize = 12.sp) }
            )
            FilterChip(
                selected = statusFilter == "ACTIVE",
                onClick = { statusFilter = "ACTIVE" },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isDark) Color(0xFF34D399) else Color(0xFF16A34A)))
                        Text("Aktif ($activeCount)", fontSize = 12.sp)
                    }
                }
            )
            FilterChip(
                selected = statusFilter == "INACTIVE",
                onClick = { statusFilter = "INACTIVE" },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)))
                        Text("Nonaktif / Habis ($inactiveCount)", fontSize = 12.sp)
                    }
                }
            )
            FilterChip(
                selected = statusFilter == "MEMBER",
                onClick = { statusFilter = "MEMBER" },
                leadingIcon = {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                    )
                },
                label = {
                    Text("Member ($memberCount)", fontSize = 12.sp)
                }
            )
            FilterChip(
                selected = statusFilter == "ONLINE",
                onClick = { statusFilter = "ONLINE" },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isDark) Color(0xFF34D399) else Color(0xFF10B981)))
                        Text("Online ($onlineCount)", fontSize = 12.sp)
                    }
                }
            )
            FilterChip(
                selected = false,
                onClick = onOpenPesan,
                leadingIcon = {
                    Icon(
                        Icons.Default.Message,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                label = {
                    Text("Pesan (${messageList.size})", fontSize = 12.sp)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Keterangan Geser & Total Data
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tabel Data Pengguna (${filteredList.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Geser ke samping untuk kolom lengkap ➔",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Memuat data pengguna dari Firebase...", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.People, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty() || statusFilter != "ALL") "Tidak ada pengguna yang cocok dengan kriteria." else "Belum ada data pengguna di cloud.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isNotEmpty() || statusFilter != "ALL") {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = {
                            onSearchQueryChange("")
                            statusFilter = "ALL"
                        }) {
                            Text("Reset Pencarian")
                        }
                    }
                }
            }
        } else {
            // STRUKTUR TABEL PENGGUNA (Rapi, Kolom Konsisten, & Scroll Horizontal)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val horizontalScrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    Column(
                        modifier = Modifier
                            .width(1060.dp)
                            .fillMaxHeight()
                    ) {
                        // BARIS HEADER TABEL
                        UserTableHeader()
                        HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        // BARIS DATA TABEL (LazyColumn vertikal di dalam kolom tabel)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            itemsIndexed(filteredList, key = { _, user -> user.uid }) { index, userItem ->
                                UserTableRow(
                                    index = index + 1,
                                    user = userItem,
                                    onCopyUid = { onCopyUid(userItem.uid) },
                                    onCopyToken = { onCopyToken(userItem.fcmToken) },
                                    onSendMessage = { onSendMessageToUser(userItem) },
                                    onToggleStatus = { onToggleUserStatus(userItem) },
                                    onManageMembership = { onManageMembership(userItem) },
                                    onOpenMembershipDetail = { onOpenMembershipDetail(userItem) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            }
                        }

                        // FOOTER TABEL: RINGKASAN STATUS
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Menampilkan ${filteredList.size} dari $totalCount pengguna",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$activeCount Aktif • $inactiveCount Nonaktif • $onlineCount Online",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Header Kolom Tabel Pengguna
 */
@Composable
fun UserTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Kolom 1: No
        Box(
            modifier = Modifier.width(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("NO", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        // Kolom 2: Pengguna
        Row(
            modifier = Modifier
                .width(220.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text("PENGGUNA", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        // Kolom 3: Status Akun
        Box(
            modifier = Modifier.width(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("STATUS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Kolom 4: Membership
        Row(
            modifier = Modifier
                .width(175.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFF59E0B))
            Text("MEMBERSHIP", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        // Kolom 5: Terakhir Akses
        Row(
            modifier = Modifier
                .width(155.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text("TERAKHIR AKSES", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        // Kolom 6: Perangkat & FCM
        Row(
            modifier = Modifier
                .width(160.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text("PERANGKAT & FCM", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        // Kolom 7: Aksi Admin
        Box(
            modifier = Modifier.width(206.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("AKSI ADMIN", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Baris Individual Data Tabel Pengguna
 */
@Composable
fun UserTableRow(
    index: Int,
    user: CloudUserItem,
    onCopyUid: () -> Unit,
    onCopyToken: () -> Unit,
    onSendMessage: () -> Unit,
    onToggleStatus: () -> Unit,
    onManageMembership: () -> Unit,
    onOpenMembershipDetail: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val isRecentlyActive = user.isRecentlyActive()
    val formattedLastActive = user.getFormattedLastActive()
    val fullDateTime = user.getFullDateTimeLastActive()

    val rowBgColor = when {
        user.isAdmin -> if (isDark) Color(0xFF064E3B).copy(alpha = 0.25f) else Color(0xFFF0FDF4)
        !user.isActive || user.isMembershipExpired() -> if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.25f) else Color(0xFFFEF2F2).copy(alpha = 0.55f)
        index % 2 == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBgColor)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Kolom 1: No
        Box(
            modifier = Modifier.width(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$index",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Kolom 2: Profil Pengguna (Avatar, Nama, Email, UID) - Klik Profil untuk Pop up Detail Membership
        Row(
            modifier = Modifier
                .width(220.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenMembershipDetail() }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppUserAvatar(
                name = user.displayName,
                imageUri = user.photoUrl,
                avatarIndex = if (user.photoUrl.isNotBlank()) -1 else -2,
                size = 34.dp,
                textSize = 12.sp,
                isLoggedIn = true,
                contentDescription = user.displayName,
                onClick = onOpenMembershipDetail
            )

            Column(modifier = Modifier.weight(1f)) {
                // Sesuai permintaan pengguna: Title "ADMIN" dibuat satu baris saja jangan lima baris
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = user.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (user.isAdmin) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isDark) Color(0xFF065F46) else Color(0xFF15803D),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = "ADMIN",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                if (user.email.isNotEmpty()) {
                    Text(
                        text = user.email,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "UID: ${user.uid.take(8)}...",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline
                    )
                    IconButton(
                        onClick = onCopyUid,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Salin UID",
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // Kolom 3: Status Akun (Aktif / Nonaktif)
        Box(
            modifier = Modifier.width(100.dp),
            contentAlignment = Alignment.Center
        ) {
            if (user.isActive && !user.isMembershipExpired()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDark) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFFDCFCE7))
                        .border(1.dp, if (isDark) Color(0xFF059669) else Color(0xFF86EFAC), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (isDark) Color(0xFF34D399) else Color(0xFF15803D)))
                        Text(
                            text = "AKTIF",
                            color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.5f) else Color(0xFFFEE2E2))
                        .border(1.dp, if (isDark) Color(0xFFDC2626) else Color(0xFFFCA5A5), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (isDark) Color(0xFFF87171) else Color(0xFFB91C1C)))
                        Text(
                            text = if (user.isMembershipExpired()) "HABIS" else "NONAKTIF",
                            color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Kolom 4: Membership (Rentan Waktu 1, 3, 5, 7, 12 Bulan) - Klik untuk Pop up Detail Membership
        Column(
            modifier = Modifier
                .width(175.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onOpenMembershipDetail() }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (user.isAdmin) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDark) Color(0xFF065F46) else Color(0xFFDCFCE7),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF059669) else Color(0xFF86EFAC))
                ) {
                    Text(
                        text = "ADMIN (PERMANEN)",
                        color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            } else if (user.isMembershipExpired()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.5f) else Color(0xFFFEE2E2),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFFDC2626) else Color(0xFFFCA5A5))
                ) {
                    Text(
                        text = "KADALUARSA",
                        color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "Habis: ${user.getFormattedMembershipEndDate()}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (user.hasMembership()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.5f) else Color(0xFFDBEAFE),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF3B82F6) else Color(0xFF93C5FD))
                    ) {
                        Text(
                            text = user.getFormattedMembershipDuration(),
                            color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "Sisa ${user.getRemainingMembershipDays()} hr",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                    )
                }
                Text(
                    text = "s.d ${user.getFormattedMembershipEndDate()}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "Belum Diatur",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Kolom 5: Terakhir Akses
        Column(
            modifier = Modifier
                .width(155.dp)
                .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isRecentlyActive) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF34D399) else Color(0xFF10B981))
                    )
                    Text(
                        text = "ONLINE",
                        color = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = formattedLastActive,
                    fontSize = 11.sp,
                    fontWeight = if (isRecentlyActive) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isRecentlyActive) (if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            if (fullDateTime != "-") {
                Text(
                    text = fullDateTime,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Kolom 6: Perangkat & Token FCM
        Column(
            modifier = Modifier
                .width(160.dp)
                .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (user.deviceModel.isNotBlank()) {
                Text(
                    text = user.deviceModel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = user.osVersion,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text("Perangkat: -", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = if (user.fcmToken.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = if (user.fcmToken.isNotBlank()) (if (isDark) Color(0xFF34D399) else Color(0xFF16A34A)) else (if (isDark) Color(0xFFFB923C) else Color(0xFFEA580C))
                )
                Text(
                    text = if (user.fcmToken.isNotBlank()) "Token Tersimpan" else "Belum Ada",
                    fontSize = 9.sp,
                    color = if (user.fcmToken.isNotBlank()) (if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D)) else MaterialTheme.colorScheme.outline
                )
                if (user.fcmToken.isNotBlank()) {
                    IconButton(
                        onClick = onCopyToken,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Salin Token",
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // Kolom 7: Aksi Admin (Pesan, Membership & Kontrol Status Akun)
        Row(
            modifier = Modifier
                .width(206.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Tombol Kirim Pesan
            OutlinedButton(
                onClick = onSendMessage,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text("Pesan", fontSize = 10.sp)
            }

            // Tombol Membership
            if (!user.isAdmin) {
                OutlinedButton(
                    onClick = onManageMembership,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Member", fontSize = 10.sp, color = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309))
                }
            }

            // Tombol Status Akun
            if (user.isAdmin) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDark) Color(0xFF065F46).copy(alpha = 0.4f) else Color(0xFF15803D).copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Admin",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else if (user.isActive && !user.isMembershipExpired()) {
                OutlinedButton(
                    onClick = onToggleStatus,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Nonaktif", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false)
                }
            } else {
                Button(
                    onClick = onToggleStatus,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF059669) else Color(0xFF16A34A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Aktifkan", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/**
 * Dialog Atur Masa Membership Pengguna (1, 3, 5, 7, dan 12 Bulan)
 */
@Composable
fun UserMembershipDialog(
    user: CloudUserItem,
    isApplying: Boolean,
    adminPhoneNumber: String = "088806643415",
    onDismiss: () -> Unit,
    onApplyMembership: (months: Int, activateAccount: Boolean) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val membershipOptions = remember {
        listOf(
            1 to "1 Bulan (30 Hari)",
            3 to "3 Bulan (90 Hari)",
            5 to "5 Bulan (150 Hari)",
            7 to "7 Bulan (210 Hari)",
            12 to "12 Bulan (1 Tahun / 365 Hari)",
            0 to "Tanpa Batas / Reset Membership"
        )
    }

    var selectedMonths by remember {
        mutableIntStateOf(
            if (user.membershipDurationMonths in listOf(1, 3, 5, 7, 12)) user.membershipDurationMonths else 1
        )
    }
    var autoActivate by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!isApplying) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Atur Membership Pengguna",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Info Profil Pengguna
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = user.displayName.ifEmpty { "Pengguna" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (user.email.isNotEmpty()) {
                            Text(text = user.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = "UID: ${user.uid}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Status Akun:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (user.isActive && !user.isMembershipExpired()) "Aktif" else if (user.isMembershipExpired()) "Kedaluwarsa" else "Nonaktif",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (user.isActive && !user.isMembershipExpired()) (if (isDark) Color(0xFF34D399) else Color(0xFF15803D)) else MaterialTheme.colorScheme.error
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Membership Saat Ini:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = user.getFormattedMembershipPeriod(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (user.isMembershipExpired()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        if (user.hasMembership()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "Berlaku Sampai:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = user.getFormattedMembershipEndDate(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Desain Kartu VIP Membership Sesuai Pilihan (1, 3, 5, 7, 12 Bulan)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Pratinjau Kartu VIP Membership:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    VipMembershipCard(
                        userName = user.displayName,
                        memberId = user.uid,
                        durationMonths = selectedMonths,
                        startDate = System.currentTimeMillis(),
                        endDate = if (selectedMonths > 0) {
                            val cal = java.util.Calendar.getInstance()
                            cal.add(java.util.Calendar.MONTH, selectedMonths)
                            cal.timeInMillis
                        } else 0L,
                        isActive = autoActivate,
                        isAdmin = user.isAdmin,
                        adminPhoneNumber = adminPhoneNumber,
                        userEmail = user.email,
                        cardSide = VipCardSide.MEMBER,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Pilihan Durasi Membership (1, 3, 5, 7, 12 Bulan)
                Text(
                    text = "Pilih Durasi Masa Berlaku:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                membershipOptions.forEach { (months, label) ->
                    val isSelected = selectedMonths == months
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.25f else 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMonths = months }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedMonths = months }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (months > 0) {
                                    val cal = java.util.Calendar.getInstance()
                                    cal.add(java.util.Calendar.MONTH, months)
                                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id", "ID"))
                                    Text(
                                        text = "Berakhir sekitar: ${sdf.format(cal.time)}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            if (months in listOf(1, 3, 5, 7, 12)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "$months Bln",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Checkbox Otomatis Aktifkan Akun
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { autoActivate = !autoActivate }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = autoActivate,
                        onCheckedChange = { autoActivate = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Otomatis aktifkan akun saat membership diterapkan",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Box Peringatan Proteksi Otomatis
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f) else Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Proteksi Otomatis: Jika waktu membership habis, akun pengguna akan otomatis dinonaktifkan. Pengguna tidak akan bisa login dan saat aplikasi dibuka akan langsung diblokir.",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApplyMembership(selectedMonths, autoActivate)
                },
                enabled = !isApplying
            ) {
                if (isApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Terapkan Membership", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isApplying
            ) {
                Text("Batal")
            }
        }
    )
}

/**
 * Konten Sub Menu 2: Pesan (Hanya Admin yang dapat menulis dan menghapus pesan)
 */
@Composable
fun PesanAdminContent(
    messageList: List<CloudMessageItem>,
    isLoading: Boolean,
    onWriteMessageClick: () -> Unit,
    onDeleteMessageClick: (CloudMessageItem) -> Unit,
    onDeleteAllClick: (() -> Unit)? = null,
    onBackToUsers: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Tombol Kembali ke Tabel Pengguna
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = false,
                onClick = onBackToUsers,
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                label = { Text("Kembali ke Tabel Pengguna", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Bar Atas: Tulis Pesan & Hapus Semua
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onWriteMessageClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tulis Pesan", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }

            if (messageList.isNotEmpty() && onDeleteAllClick != null) {
                OutlinedButton(
                    onClick = onDeleteAllClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hapus Semua", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Badge Aturan Keamanan
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF78350F).copy(alpha = 0.35f) else Color(0xFFFEF3C7)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Shield, null, tint = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706), modifier = Modifier.size(18.dp))
                Text(
                    text = "Aturan Keamanan: Hanya akun Admin (UID: ${FirebaseAccountAdminManager.ADMIN_UID}) yang dapat membuat dan menghapus pesan di koleksi 'messages'.",
                    fontSize = 10.sp,
                    color = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E),
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Memuat pesan dari Firestore...", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else if (messageList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Message, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Belum ada pesan yang dibuat.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onWriteMessageClick) {
                        Text("Mulai Tulis Pesan Pertama")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(messageList, key = { it.id }) { msg ->
                    MessageItemCard(
                        message = msg,
                        onDelete = { onDeleteMessageClick(msg) }
                    )
                }
            }
        }
    }
}

/**
 * Kartu Pesan Individual
 */
@Composable
fun MessageItemCard(
    message: CloudMessageItem,
    onDelete: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val categoryColor = when (message.category) {
        "PERINGATAN" -> Color(0xFFDC2626)
        "PEMBARUAN" -> if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
        "INFORMASI" -> if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
        else -> MaterialTheme.colorScheme.primary
    }

    val formattedDate = remember(message.createdAt) {
        try {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            sdf.format(Date(message.createdAt))
        } catch (_: Exception) {
            "-"
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Kategori, Prioritas & Tombol Hapus Admin
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(categoryColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = message.category,
                            color = categoryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (message.priority != "NORMAL") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isDark) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else Color(0xFFFEF2F2))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = message.priority,
                                color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Target: ${message.targetName}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Tombol Hapus Permanen dari Firestore (Khusus Admin)
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Hapus Pesan Permanen dari Firestore (Khusus Admin UID Terdaftar)",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Judul
            Text(
                text = message.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Isi Pesan
            Text(
                text = message.content,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(6.dp))

            // Footer: Waktu & Penulis (Admin)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Penulis: ${message.authorName}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formattedDate,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * Dialog Tulis Pesan Baru (Khusus Admin)
 */
@Composable
fun WriteMessageDialog(
    targetUser: CloudUserItem?,
    onDismiss: () -> Unit,
    onSubmitSingle: (title: String, content: String, category: String, priority: String, targetUid: String, targetName: String) -> Unit
) {
    // Single Message Form State
    var singleTitle by remember { mutableStateOf("") }
    var singleContent by remember { mutableStateOf("") }
    var singleCategory by remember { mutableStateOf("PENGUMUMAN") }
    var singlePriority by remember { mutableStateOf("NORMAL") }

    val categories = listOf("PENGUMUMAN", "INFORMASI", "PEMBARUAN", "PERINGATAN")
    val priorities = listOf("NORMAL", "TINGGI", "MENDESAK")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.PostAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (targetUser != null) "Kirim Pesan ke Pengguna" else "Tulis Pesan Siaran",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (targetUser != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Target: ${targetUser.displayName} (${targetUser.email.ifEmpty { targetUser.uid.take(10) }})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Judul
                OutlinedTextField(
                    value = singleTitle,
                    onValueChange = { singleTitle = it },
                    label = { Text("Judul Pesan *") },
                    placeholder = { Text("Contoh: Pengumuman Pembaruan Sistem") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Kategori
                Text("Kategori:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(2).forEach { cat ->
                        FilterChip(
                            selected = singleCategory == cat,
                            onClick = { singleCategory = cat },
                            label = { Text(cat, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.drop(2).forEach { cat ->
                        FilterChip(
                            selected = singleCategory == cat,
                            onClick = { singleCategory = cat },
                            label = { Text(cat, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Prioritas
                Text("Prioritas:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    priorities.forEach { prio ->
                        FilterChip(
                            selected = singlePriority == prio,
                            onClick = { singlePriority = prio },
                            label = { Text(prio, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Isi Pesan
                OutlinedTextField(
                    value = singleContent,
                    onValueChange = { singleContent = it },
                    label = { Text("Isi Pesan *") },
                    placeholder = { Text("Tulis isi pesan pengumuman...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (singleTitle.isNotBlank() && singleContent.isNotBlank()) {
                        val targetUid = targetUser?.uid ?: "SEMUA"
                        val targetName = targetUser?.displayName ?: "Semua Pengguna"
                        onSubmitSingle(singleTitle.trim(), singleContent.trim(), singleCategory, singlePriority, targetUid, targetName)
                    }
                },
                enabled = singleTitle.isNotBlank() && singleContent.isNotBlank()
            ) {
                Text("Kirim ke Cloud")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

/**
 * Dialog Aturan Keamanan Firestore Rules
 */
@Composable
fun FirestoreRulesDialog(
    onDismiss: () -> Unit,
    onCopyRules: () -> Unit
) {
    val adminManager = remember { FirebaseAccountAdminManager.getInstance() }
    val rulesCode = remember { adminManager.getFirestoreSecurityRulesCode() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                Text("Aturan Rules Firestore", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Aturan resmi ini menjamin hanya Administrator (UID: ${FirebaseAccountAdminManager.ADMIN_UID}) yang dapat menulis dan menghapus pesan, serta mengizinkan penyimpanan token FCM otomatis:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .padding(10.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = rulesCode,
                            color = Color(0xFFF1F5F9),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCopyRules) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Salin Aturan Rules")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

/**
 * Dialog Pesan Masuk Kode OTP untuk Administrator
 * Ditampilkan saat Admin menekan icon email di sebelah kiri icon titik 3
 */
@Composable
fun AdminOtpInboxDialog(
    otpList: List<com.example.firebase.AdminOtpRequest>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit,
    onMarkUsed: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val activeCount = otpList.count { it.status == "MENUNGGU" && !it.isExpired }
    val dateFormat = remember { SimpleDateFormat("HH:mm - dd MMM yyyy", Locale("id", "ID")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0F766E).copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pesan Masuk Kode OTP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Proteksi Sandi Email (Admin: UiDpHxawlBQ17mxTUHIXvhxRR3b2)",
                        fontSize = 10.5.sp,
                        color = Color(0xFF0F766E),
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Info Bar: Jumlah Kode Menunggu
                Surface(
                    color = if (activeCount > 0) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (activeCount > 0) "• $activeCount Kode OTP Menunggu Verifikasi" else "Tidak ada kode OTP yang menunggu",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (activeCount > 0) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Total: ${otpList.size}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (otpList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MarkEmailRead,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Belum Ada Pesan Masuk Kode OTP",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Saat pengguna meminta kode OTP untuk kata sandi email di Profil, kode verifikasi akan muncul di sini.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(otpList.size) { index ->
                            val req = otpList[index]
                            val isPending = req.status == "MENUNGGU" && !req.isExpired
                            val isUsed = req.status == "DIGUNAKAN"
                            val isExpired = req.isExpired && req.status == "MENUNGGU"

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isPending) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isPending) Color(0xFF0F766E).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Row 1: Requester & Status Badge
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = req.requesterName.ifBlank { "Pengguna My Singghara" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = req.requesterEmail,
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF0F766E),
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when {
                                                isUsed -> Color(0xFFDCFCE7)
                                                isExpired -> Color(0xFFFEE2E2)
                                                else -> Color(0xFFFEF3C7)
                                            }
                                        ) {
                                            Text(
                                                text = when {
                                                    isUsed -> "TERVERIFIKASI"
                                                    isExpired -> "KADALUARSA"
                                                    else -> "MENUNGGU"
                                                },
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isUsed -> Color(0xFF15803D)
                                                    isExpired -> Color(0xFFB91C1C)
                                                    else -> Color(0xFFB45309)
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "UID: ${req.requesterUid}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Waktu: ${dateFormat.format(Date(req.createdAt))}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Highlight Box Kode OTP
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isPending) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, if (isPending) Color(0xFF10B981) else Color.Transparent),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = "KODE OTP VERIFIKASI",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isPending) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = req.otpCode.chunked(1).joinToString("  "),
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (isPending) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            // Tombol Salin Kode Cepat
                                            OutlinedButton(
                                                onClick = {
                                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(req.otpCode))
                                                    Toast.makeText(context, "Kode OTP ${req.otpCode} berhasil disalin!", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F766E)),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Salin", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Action Buttons Row: WhatsApp balasan & Hapus
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val replyText = "Halo ${req.requesterName.ifBlank { "Pengguna" }}, kode verifikasi OTP My Singghara untuk atur kata sandi email Anda adalah: *${req.otpCode}*. Kode berlaku 15 menit."

                                        Button(
                                            onClick = {
                                                try {
                                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, replyText)
                                                    }
                                                    context.startActivity(Intent.createChooser(sendIntent, "Bagikan Kode OTP ke Pengguna"))
                                                } catch (_: Exception) {}
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Icon(Icons.Default.Share, null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Kirim Kode", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(replyText))
                                                Toast.makeText(context, "Format pesan balasan berhasil disalin!", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Salin Balasan", fontSize = 10.sp)
                                        }

                                        IconButton(
                                            onClick = { onDelete(req.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
