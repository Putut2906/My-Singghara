package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import coil.compose.AsyncImage

@Composable
fun BottomNavBar(viewModel: MainViewModel) {
    val currentRoute = viewModel.currentRoute
    val isAppLocked = viewModel.isAppLockEnabled && !viewModel.isAppUnlocked

    // Unified Surface container that extends seamlessly to the bottom edge of the screen without gaps
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Kontainer dalam untuk menyatukan row menu dan FAB yang melayang di tengah
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Horisontal Navigation Items Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // Menu 1: Home
                    NavBarItem(
                        label = "Home",
                        icon = Icons.Default.Home,
                        selected = currentRoute == "home",
                        onClick = {
                            if (isAppLocked) return@NavBarItem
                            viewModel.currentRoute = "home"
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Menu 2: Kasir
                    NavBarItem(
                        label = "Kasir",
                        icon = Icons.Default.PointOfSale,
                        selected = currentRoute == "my_kasir",
                        onClick = {
                            if (isAppLocked) return@NavBarItem
                            viewModel.currentRoute = "my_kasir"
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Menu 3: Belanja
                    NavBarItem(
                        label = "Belanja",
                        icon = Icons.Default.ShoppingCart,
                        selected = currentRoute == "belanja",
                        onClick = {
                            if (isAppLocked) return@NavBarItem
                            viewModel.currentRoute = "belanja"
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Placeholder Spacer di baris bawah untuk tempat melayangnya FAB di tengah
                    Spacer(modifier = Modifier.weight(1f))

                    // Menu 4: Catatan
                    NavBarItem(
                        label = "Catatan",
                        icon = Icons.Default.StickyNote2,
                        selected = currentRoute == "catatan",
                        onClick = {
                            if (isAppLocked) return@NavBarItem
                            viewModel.currentRoute = "catatan"
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Menu 5: Bagikan
                    NavBarItem(
                        label = "Bagikan",
                        icon = Icons.Default.Share,
                        selected = currentRoute == "share",
                        onClick = {
                            if (isAppLocked) return@NavBarItem
                            viewModel.currentRoute = "share"
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Menu 6: Profil
                    val isProfilSelected = currentRoute == "profil"
                    NavBarItem(
                        label = "Profil",
                        icon = Icons.Default.Person,
                        selected = isProfilSelected,
                        onClick = {
                            if (isAppLocked) return@NavBarItem
                            viewModel.currentRoute = "profil"
                        },
                        modifier = Modifier.weight(1f),
                        customIcon = {
                            val avatarGradients = listOf(
                                Brush.linearGradient(listOf(Color(0xFF2A84EB), Color(0xFF1456A5))),
                                Brush.linearGradient(listOf(Color(0xFFFF5E62), Color(0xFFFF9966))),
                                Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
                                Brush.linearGradient(listOf(Color(0xFF00C6FF), Color(0xFF0072FF))),
                                Brush.linearGradient(listOf(Color(0xFF8A2387), Color(0xFFE94057)))
                            )
                            val avatarIcons = listOf(
                                Icons.Default.Person,
                                Icons.Default.Face,
                                Icons.Default.Star,
                                Icons.Default.Favorite,
                                Icons.Default.Palette
                            )

                            val borderModifier = if (isProfilSelected) {
                                Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            }

                            com.example.ui.components.AppUserAvatar(
                                name = viewModel.profileName,
                                imageUri = viewModel.profileImageUri,
                                avatarIndex = viewModel.profileAvatarIndex,
                                size = 26.dp,
                                textSize = 10.sp,
                                isLoggedIn = viewModel.currentFirebaseUser != null,
                                contentDescription = "Profile Bar Icon",
                                modifier = Modifier
                                    .size(26.dp)
                                    .then(borderModifier)
                                    .padding(if (isProfilSelected) 1.5.dp else 0.dp)
                            )
                        }
                    )
                }

                // ELEVATED PROMINENT FAB "+", melayang indah vertikal di tengah area
                FloatingActionButton(
                    onClick = {
                        if (!isAppLocked) {
                            viewModel.currentRoute = "tabungan"
                            viewModel.showTabunganFabDialog = true
                        }
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.TopCenter),
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Menu Tabungan Instan",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NavBarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    customIcon: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (customIcon != null) {
                customIcon()
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
