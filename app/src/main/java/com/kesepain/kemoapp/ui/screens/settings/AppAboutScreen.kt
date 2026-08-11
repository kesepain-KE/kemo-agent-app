package com.kesepain.kemoapp.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.components.LoadingButton
import com.kesepain.kemoapp.ui.components.LoadingFilledTonalButton
import com.kesepain.kemoapp.ui.components.LoadingOutlinedButton
import com.kesepain.kemoapp.update.AppAboutUiState
import com.kesepain.kemoapp.update.AppDownloadSource
import com.kesepain.kemoapp.update.AppUpdateRepository
import com.kesepain.kemoapp.update.AppUpdateUiState
import com.kesepain.kemoapp.update.GitHubRelease

@Composable
fun AppAboutScreen(
    state: AppAboutUiState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onCheckUpdate: (Boolean) -> Unit,
    onSelectDownloadSource: (String) -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val appVersion = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty().ifBlank { "—" }
    }
    LaunchedEffect(Unit) {
        onLoad()
        if (state.update is AppUpdateUiState.Idle) onCheckUpdate(false)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                Text(
                    stringResource(R.string.about),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        item { AppIdentityCard(appVersion) }
        item { MaintainerCard(state.avatarBytes) { openExternalUrl(context, AppUpdateRepository.GITHUB_PROFILE_URL) } }

        item { Text(stringResource(R.string.about_project_information), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    AboutInfoRow(
                        icon = Icons.Outlined.PhoneAndroid,
                        label = stringResource(R.string.about_app_ui_version),
                        value = appVersion,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    AboutInfoRow(
                        icon = Icons.Outlined.Description,
                        label = stringResource(R.string.about_open_source_license),
                        value = stringResource(R.string.about_license_apache_2),
                        onClick = { openExternalUrl(context, AppUpdateRepository.APACHE_LICENSE_URL) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    AboutInfoRow(
                        icon = Icons.Outlined.Link,
                        label = stringResource(R.string.about_project_link),
                        value = stringResource(R.string.about_project_link_value),
                        onClick = { openExternalUrl(context, AppUpdateRepository.GITHUB_PROJECT_URL) },
                    )
                }
            }
        }

        item { Text(stringResource(R.string.about_updates), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
        item {
            UpdateCard(
                currentVersion = appVersion,
                update = state.update,
                selectedSourceId = state.selectedDownloadSourceId,
                downloadSources = AppUpdateRepository.DOWNLOAD_SOURCES,
                onCheck = { onCheckUpdate(true) },
                onSelectSource = onSelectDownloadSource,
                onDownload = onDownloadUpdate,
                onInstall = onInstallUpdate,
                onOpenReleases = { openExternalUrl(context, AppUpdateRepository.GITHUB_RELEASES_URL) },
            )
        }
    }
}

@Composable
private fun AppIdentityCard(appVersion: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(84.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
            ) {
                Image(
                    painter = painterResource(R.drawable.kemo_brand_internal),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.padding(8.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Column(Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AboutPill("v$appVersion")
                    AboutPill(stringResource(R.string.about_platform_android))
                }
            }
        }
    }
}

@Composable
private fun MaintainerCard(avatarBytes: ByteArray?, onOpenProfile: () -> Unit) {
    val avatar = rememberAvatar(avatarBytes)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (avatar != null) {
                    Image(
                        bitmap = avatar,
                        contentDescription = stringResource(R.string.about_github_avatar),
                        modifier = Modifier.size(88.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.kesepain_github_avatar),
                        contentDescription = stringResource(R.string.about_github_avatar),
                        modifier = Modifier.size(88.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("kesepain", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.about_maintainer),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "@kesepain-KE",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LoadingFilledTonalButton(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
            ) {
                Icon(Icons.Outlined.Code, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.about_open_github_profile))
            }
        }
    }
}

@Composable
private fun AboutInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Row(
        modifier = Modifier.fillMaxWidth().then(clickModifier).sizeIn(minHeight = 76.dp).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onClick != null) Icon(Icons.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun UpdateCard(
    currentVersion: String,
    update: AppUpdateUiState,
    selectedSourceId: String,
    downloadSources: List<AppDownloadSource>,
    onCheck: () -> Unit,
    onSelectSource: (String) -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleases: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(44.dp), CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(22.dp))
                    }
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(stringResource(R.string.about_update_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.about_current_version, currentVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (update) {
                AppUpdateUiState.Idle -> {
                    UpdateStatus(Icons.Outlined.Info, stringResource(R.string.about_update_not_checked))
                    LoadingOutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.about_check_update))
                    }
                }
                AppUpdateUiState.Checking -> {
                    UpdateStatus(Icons.Outlined.Refresh, stringResource(R.string.about_update_checking))
                    LoadingOutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth(), loading = true) { Text(stringResource(R.string.about_update_checking)) }
                }
                is AppUpdateUiState.Available -> {
                    ReleaseSummary(update.release)
                    UpdateStatus(Icons.Outlined.SystemUpdate, stringResource(R.string.about_update_available, update.release.tagName), MaterialTheme.colorScheme.primary)
                    DownloadSourceSelector(
                        selectedSourceId = selectedSourceId,
                        sources = downloadSources,
                        onSelected = onSelectSource,
                    )
                    LoadingButton(onClick = onDownload, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                        Icon(Icons.Outlined.Download, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.about_download_update))
                    }
                }
                is AppUpdateUiState.UpToDate -> {
                    ReleaseSummary(update.release)
                    UpdateStatus(Icons.Outlined.CheckCircleOutline, stringResource(R.string.about_update_latest), MaterialTheme.colorScheme.primary)
                    LoadingOutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.about_check_again))
                    }
                }
                is AppUpdateUiState.ReleaseWithoutApk -> {
                    ReleaseSummary(update.release)
                    UpdateStatus(Icons.Outlined.Info, stringResource(R.string.about_update_no_apk))
                    LoadingOutlinedButton(onClick = onOpenReleases, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.OpenInNew, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.about_open_releases))
                    }
                }
                AppUpdateUiState.NoPublishedRelease -> {
                    UpdateStatus(Icons.Outlined.Info, stringResource(R.string.about_update_no_release))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LoadingOutlinedButton(onClick = onCheck, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.about_check_again)) }
                        LoadingFilledTonalButton(onClick = onOpenReleases, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.about_open_releases)) }
                    }
                }
                AppUpdateUiState.Failed -> {
                    UpdateStatus(Icons.Outlined.ErrorOutline, stringResource(R.string.about_update_check_failed), MaterialTheme.colorScheme.error)
                    LoadingOutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.retry))
                    }
                }
                is AppUpdateUiState.Downloading -> {
                    ReleaseSummary(update.release)
                    UpdateStatus(
                        Icons.Outlined.Download,
                        stringResource(
                            R.string.about_update_source_active,
                            update.source?.displayName ?: stringResource(R.string.about_download_source_auto),
                        ),
                        MaterialTheme.colorScheme.primary,
                    )
                    LinearProgressIndicator(
                        progress = { update.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Text(
                        stringResource(R.string.about_update_downloading, update.progress),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LoadingButton(onClick = {}, modifier = Modifier.fillMaxWidth(), loading = true) { Text(stringResource(R.string.about_update_downloading_button)) }
                }
                is AppUpdateUiState.Downloaded -> {
                    ReleaseSummary(update.release)
                    UpdateStatus(Icons.Outlined.CheckCircleOutline, stringResource(R.string.about_update_downloaded), MaterialTheme.colorScheme.primary)
                    UpdateStatus(
                        Icons.Outlined.CheckCircleOutline,
                        stringResource(R.string.about_update_verified, update.source.displayName),
                        MaterialTheme.colorScheme.primary,
                    )
                    LoadingButton(onClick = onInstall, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                        Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.about_install_update))
                    }
                }
                is AppUpdateUiState.DownloadFailed -> {
                    ReleaseSummary(update.release)
                    UpdateStatus(Icons.Outlined.ErrorOutline, stringResource(R.string.about_update_download_failed), MaterialTheme.colorScheme.error)
                    update.lastSource?.let { failedSource ->
                        Text(
                            stringResource(R.string.about_update_failed_source, failedSource.displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadSourceSelector(
                        selectedSourceId = selectedSourceId,
                        sources = downloadSources,
                        onSelected = onSelectSource,
                    )
                    LoadingOutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Download, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseSummary(release: GitHubRelease) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(release.title.ifBlank { release.tagName }, style = MaterialTheme.typography.titleMedium)
        val size = release.apkSize.takeIf { it > 0L }?.let { Formatter.formatShortFileSize(context, it) }
        Text(
            listOfNotNull(release.tagName.takeIf(String::isNotBlank), size).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (release.apkSha256.isNotBlank()) {
            Text(
                stringResource(R.string.about_update_sha256, release.apkSha256.take(12)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadSourceSelector(
    selectedSourceId: String,
    sources: List<AppDownloadSource>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = sources.firstOrNull { it.id == selectedSourceId }
    val selectedLabel = selected?.displayName ?: stringResource(R.string.about_download_source_auto)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            stringResource(R.string.about_download_source),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.fillMaxWidth()) {
            LoadingOutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(selectedLabel, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(22.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.about_download_source_auto)) },
                    onClick = {
                        expanded = false
                        onSelected(AppUpdateRepository.AUTO_DOWNLOAD_SOURCE_ID)
                    },
                )
                sources.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source.displayName) },
                        onClick = {
                            expanded = false
                            onSelected(source.id)
                        },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.about_download_source_hint, sources.count { !it.official }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.about_download_source_security),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun UpdateStatus(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, modifier = Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
private fun AboutPill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rememberAvatar(bytes: ByteArray?): ImageBitmap? = remember(bytes?.contentHashCode()) {
    bytes?.let { value ->
        runCatching { BitmapFactory.decodeByteArray(value, 0, value.size)?.asImageBitmap() }.getOrNull()
    }
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
