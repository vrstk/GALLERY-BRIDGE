package com.gallerybridge.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.R

internal enum class GalleryPage(val label: String, val title: String = label) {
    HOME("Главная", "GALLERY BRIDGE"),
    DOWNLOADS("Загрузки"),
    BROWSER("Браузер", "Браузер медиа"),
    QUEUE("Очередь", "Очередь загрузок"),
    HISTORY("История", "История загрузок"),
    SETTINGS("Настройки")
}

private val NavigationPages = listOf(GalleryPage.HOME, GalleryPage.DOWNLOADS, GalleryPage.QUEUE, GalleryPage.HISTORY)
internal val GalleryContentWidth = 640.dp

@Composable
internal fun GalleryShell(
    page: GalleryPage,
    onNavigate: (GalleryPage) -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        contentColor = colors.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(color = colors.background) {
                Box(
                    Modifier.fillMaxWidth().windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        Modifier.widthIn(max = GalleryContentWidth).fillMaxWidth().heightIn(min = 68.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (page) {
                            GalleryPage.HOME -> Image(
                                painterResource(R.drawable.ic_gallery_bridge), null, Modifier.size(42.dp)
                            )
                            GalleryPage.SETTINGS -> IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад")
                            }
                            else -> Unit
                        }
                        Text(
                            page.title,
                            modifier = Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (page != GalleryPage.SETTINGS) {
                            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Настройки") }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (page != GalleryPage.SETTINGS) {
                Surface(color = colors.surface) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        actions()
                        NavigationBar(
                            modifier = Modifier.widthIn(max = GalleryContentWidth),
                            containerColor = colors.surface,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                        ) {
                            NavigationPages.forEach { destination ->
                                NavigationBarItem(
                                    selected = page == destination,
                                    onClick = { onNavigate(destination) },
                                    icon = {
                                        when (destination) {
                                            GalleryPage.HOME -> Icon(Icons.Outlined.Home, null)
                                            GalleryPage.DOWNLOADS -> NavigationIcon(R.drawable.ic_download)
                                            GalleryPage.QUEUE -> Icon(Icons.AutoMirrored.Outlined.List, null)
                                            GalleryPage.HISTORY -> NavigationIcon(R.drawable.ic_history)
                                            GalleryPage.SETTINGS -> Unit
                                        }
                                    },
                                    label = {
                                        Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colors.onPrimaryContainer,
                                        selectedTextColor = colors.primary,
                                        indicatorColor = colors.primaryContainer,
                                        unselectedIconColor = colors.onSurfaceVariant,
                                        unselectedTextColor = colors.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { insets ->
        Box(
            Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            key(page) { content() }
        }
    }
}

@Composable
internal fun GalleryPageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.widthIn(max = GalleryContentWidth).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun NavigationIcon(@DrawableRes id: Int) {
    Icon(painterResource(id), null)
}
