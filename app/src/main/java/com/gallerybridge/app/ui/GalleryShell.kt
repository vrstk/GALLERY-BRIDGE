package com.gallerybridge.app.ui

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.R

internal enum class GalleryPage(val label: String, val title: String = label) {
    HOME("Каталог", "GALLERY BRIDGE"),
    CATALOG("Галереи", "Каталог галерей"),
    DOWNLOADS("Загрузки"),
    QUEUE("Очередь", "Очередь загрузок"),
    HISTORY("История", "История загрузок"),
    SETTINGS("Хранилища", "Настройки")
}

// Queue and History remain separate pages, accessible from the Downloads toolbar.
private val NavigationPages = listOf(
    GalleryPage.HOME, GalleryPage.CATALOG, GalleryPage.DOWNLOADS, GalleryPage.SETTINGS
)
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Scaffold(
        containerColor = colors.background,
        contentColor = colors.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(color = colors.surface) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.horizontalGradient(
                            listOf(colors.surface, colors.primaryContainer, colors.surface)
                        ))
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        Modifier.widthIn(max = GalleryContentWidth).fillMaxWidth()
                            .heightIn(min = if (isLandscape) 54.dp else 66.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (page) {
                            GalleryPage.HOME -> Image(
                                painterResource(R.drawable.ic_gallery_bridge), null,
                                Modifier.size(if (isLandscape) 36.dp else 42.dp)
                            )
                            GalleryPage.SETTINGS, GalleryPage.CATALOG,
                            GalleryPage.QUEUE, GalleryPage.HISTORY -> IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад")
                            }
                            else -> Unit
                        }
                        Text(
                            if (page == GalleryPage.HOME) buildAnnotatedString {
                                append("GALLERY ")
                                withStyle(SpanStyle(color = colors.primary)) { append("BRIDGE") }
                            } else AnnotatedString(page.title),
                            modifier = Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (page == GalleryPage.DOWNLOADS) {
                            IconButton(
                                onClick = { onNavigate(GalleryPage.QUEUE) },
                                modifier = Modifier.testTag("downloads-queue-shortcut")
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.List,
                                    contentDescription = "Открыть очередь")
                            }
                            IconButton(
                                onClick = { onNavigate(GalleryPage.HISTORY) },
                                modifier = Modifier.testTag("downloads-history-shortcut")
                            ) {
                                Icon(painterResource(R.drawable.ic_history),
                                    contentDescription = "Открыть историю")
                            }
                        }
                        if (page != GalleryPage.SETTINGS && page != GalleryPage.CATALOG) {
                            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Настройки") }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = colors.surfaceContainerLowest) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    actions()
                    HorizontalDivider(color = colors.outlineVariant)
                    if (isLandscape) {
                        CompactGalleryNavigation(page, onNavigate)
                    } else {
                        NavigationBar(
                            modifier = Modifier.widthIn(max = GalleryContentWidth),
                            containerColor = colors.surfaceContainerLowest,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                            )
                        ) {
                            NavigationPages.forEach { destination ->
                                NavigationBarItem(
                                    selected = isSelectedDestination(page, destination),
                                    onClick = { onNavigate(destination) },
                                    modifier = Modifier.testTag("nav-${destination.name.lowercase()}"),
                                    icon = { GalleryNavigationIcon(destination) },
                                    label = {
                                        Text(
                                            destination.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
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
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

private fun isSelectedDestination(page: GalleryPage, destination: GalleryPage): Boolean =
    page == destination ||
        (destination == GalleryPage.DOWNLOADS &&
            page in listOf(GalleryPage.QUEUE, GalleryPage.HISTORY))

/**
 * Landscape uses a 52dp navigation row instead of Material's tall bottom bar.
 * System navigation insets remain outside the row, so content stays tappable.
 */
@Composable
private fun CompactGalleryNavigation(
    page: GalleryPage,
    onNavigate: (GalleryPage) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.widthIn(max = GalleryContentWidth).fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                )
            )
            .testTag("landscape-bottom-navigation"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationPages.forEach { destination ->
            val selectedTab = isSelectedDestination(page, destination)
            Column(
                modifier = Modifier.weight(1f).height(52.dp)
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Tab) { onNavigate(destination) }
                    .semantics { selected = selectedTab }
                    .testTag("nav-${destination.name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.width(52.dp).height(25.dp)
                        .clip(CircleShape)
                        .background(
                            if (selectedTab) colors.primaryContainer
                            else colors.surfaceContainerLowest
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    GalleryNavigationIcon(destination, Modifier.size(20.dp))
                }
                Text(
                    destination.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedTab) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GalleryNavigationIcon(
    destination: GalleryPage,
    modifier: Modifier = Modifier
) {
    when (destination) {
        GalleryPage.HOME -> Icon(Icons.Outlined.Home, null, modifier)
        // Use a dedicated transparent 24dp icon, not the full-color app launcher
        // vector (which turns into an opaque square under Material icon tint).
        GalleryPage.CATALOG -> NavigationIcon(
            R.drawable.ic_galleries, modifier.testTag("nav-galleries-icon")
        )
        GalleryPage.DOWNLOADS -> NavigationIcon(R.drawable.ic_download, modifier)
        GalleryPage.SETTINGS -> Icon(Icons.Outlined.Settings, null, modifier)
        GalleryPage.QUEUE, GalleryPage.HISTORY -> Unit
    }
}

@Composable
private fun NavigationIcon(@DrawableRes id: Int, modifier: Modifier = Modifier) {
    Icon(painterResource(id), null, modifier)
}
