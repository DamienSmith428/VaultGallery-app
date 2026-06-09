package com.vaultgallery.ui.screens.home

import android.Manifest
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.vaultgallery.domain.model.MediaType
import com.vaultgallery.domain.model.VaultAlbum
import com.vaultgallery.domain.model.VaultMedia
import com.vaultgallery.data.security.AutoLockManager
import com.vaultgallery.ui.components.AlbumCard
import com.vaultgallery.ui.components.MediaThumbnailCard

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenAlbum: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    autoLockManager: AutoLockManager,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionsState = rememberMultiplePermissionsState(permissionsToRequest)

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMedia(uris)
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { _ ->
        viewModel.clearUrisToDelete()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.urisToDelete) {
        if (state.urisToDelete.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    state.urisToDelete
                )
                deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } catch (e: Exception) {
                android.util.Log.e("HomeScreen", "Failed to create delete request", e)
                viewModel.clearUrisToDelete()
            }
        }
    }

    // Show import message snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.importMessage) {
        state.importMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearImportMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomeTopBar(
                isSelectionMode = state.isSelectionMode,
                selectedCount = state.selectedMediaIds.size,
                onSearch = onOpenSearch,
                onSettings = onOpenSettings,
                onClearSelection = viewModel::clearSelection,
                onDeleteSelected = viewModel::moveSelectedToRecycleBin,
                onExportSelected = viewModel::exportSelected
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (state.isSelectionMode) {
                        viewModel.clearSelection()
                    } else {
                        if (permissionsState.allPermissionsGranted) {
                            autoLockManager.setTemporaryExiting(true)
                            mediaPickerLauncher.launch("*/*")
                        } else {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    }
                },
                containerColor = if (state.isSelectionMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
            ) {
                AnimatedContent(
                    targetState = state.isSelectionMode,
                    transitionSpec = {
                        (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                    },
                    label = "fab_icon"
                ) { isSelection ->
                    Icon(
                        if (isSelection) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = null
                    )
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(3) }) {
                HomeStatsBar(
                    totalCount = state.totalCount,
                    totalSize = state.totalSize,
                    sizeLimitGb = state.vaultSizeLimitGb,
                    onClick = { viewModel.setShowSizeLimitDialog(true) },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item(span = { GridItemSpan(3) }) {
                QuickAccessRow(
                    onFavorites = onOpenFavorites,
                    onRecycleBin = onOpenRecycleBin,
                    onCreateAlbum = { viewModel.setShowCreateAlbum(true) }
                )
            }

            if (state.albums.isNotEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    SectionHeader("Albums")
                }
                items(state.albums, key = { it.id }) { album ->
                    AlbumCard(
                        name = album.name,
                        mediaCount = state.allMedia.count { it.albumId == album.id },
                        onClick = { onOpenAlbum(album.id) },
                        onLongClick = { viewModel.setAlbumToRename(album) }
                    )
                }
            }

            if (state.allMedia.isNotEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    SectionHeader("All Media (${state.allMedia.size})")
                }
                items(state.allMedia, key = { it.id }) { media ->
                    MediaThumbnailCard(
                        media = media,
                        isSelected = media.id in state.selectedMediaIds,
                        isSelectionMode = state.isSelectionMode,
                        onClick = {
                            if (state.isSelectionMode) viewModel.toggleSelection(media.id)
                            else onOpenMedia(media.id)
                        },
                        onLongClick = { viewModel.toggleSelection(media.id) },
                        onMove = { viewModel.setMediaToMove(media) }
                    )
                }
            }

            if (state.allMedia.isEmpty() && state.albums.isEmpty() && !state.isImporting) {
                item(span = { GridItemSpan(3) }) {
                    EmptyVaultHint()
                }
            }

            if (state.isImporting) {
                item(span = { GridItemSpan(3) }) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    if (state.showCreateAlbumDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowCreateAlbum(false) },
            title = { Text("New Album") },
            text = {
                OutlinedTextField(
                    value = state.newAlbumName,
                    onValueChange = viewModel::setNewAlbumName,
                    label = { Text("Album name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::createAlbum,
                    enabled = state.newAlbumName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowCreateAlbum(false) }) { Text("Cancel") }
            }
        )
    }

    state.albumToRename?.let { album ->
        AlertDialog(
            onDismissRequest = { viewModel.setAlbumToRename(null) },
            title = { Text("Rename Album") },
            text = {
                OutlinedTextField(
                    value = state.renameAlbumName,
                    onValueChange = viewModel::setRenameAlbumName,
                    label = { Text("Album name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::renameAlbum,
                    enabled = state.renameAlbumName.isNotBlank() && state.renameAlbumName != album.name
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setAlbumToRename(null) }) { Text("Cancel") }
            }
        )
    }

    if (state.mediaToMove != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setMediaToMove(null) },
            sheetState = sheetState
        ) {
            MoveToAlbumSheet(
                albums = state.albums,
                currentAlbumId = state.mediaToMove?.albumId,
                onAlbumSelected = viewModel::moveToAlbum
            )
        }
    }

    if (state.showSizeLimitDialog) {
        SizeLimitDialog(
            currentLimitGb = state.vaultSizeLimitGb,
            onDismiss = { viewModel.setShowSizeLimitDialog(false) },
            onConfirm = viewModel::updateVaultSizeLimit
        )
    }
}

@Composable
fun HomeStatsBar(
    totalCount: Int,
    totalSize: Long,
    sizeLimitGb: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSizeMB = totalSize / (1024 * 1024)
    val totalSizeGB = totalSize.toDouble() / (1024 * 1024 * 1024)
    val sizeText = if (totalSizeGB >= 1.0) "%.1f GB".format(totalSizeGB) else "$totalSizeMB MB"
    
    val capacityGB = sizeLimitGb.toDouble()
    val progress = (totalSize.toDouble() / (capacityGB * 1024 * 1024 * 1024)).coerceIn(0.0, 1.0).toFloat()
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "stats_progress"
    )

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Vault Storage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("$totalCount items", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                Text(sizeText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("of $sizeLimitGb GB", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun SizeLimitDialog(
    currentLimitGb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var limit by remember { mutableIntStateOf(currentLimitGb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vault Size Limit") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Set how much space your vault can use.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { if (limit > 1) limit-- },
                        enabled = limit > 1
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    Text(
                        text = "$limit GB",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    IconButton(
                        onClick = { limit++ }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(limit) }) {
                Text("Set Limit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    isSelectionMode: Boolean,
    selectedCount: Int,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExportSelected: () -> Unit
) {
    if (isSelectionMode) {
        TopAppBar(
            title = { Text("$selectedCount selected") },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Clear selection")
                }
            },
            actions = {
                IconButton(onClick = onExportSelected) {
                    Icon(Icons.Default.Download, contentDescription = "Export selected")
                }
                IconButton(onClick = onDeleteSelected) {
                    Icon(Icons.Default.Delete, contentDescription = "Move to recycle bin", tint = MaterialTheme.colorScheme.error)
                }
            }
        )
    } else {
        TopAppBar(
            title = {
                Text("Vault", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            },
            actions = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        )
    }
}

@Composable
private fun QuickAccessRow(
    onFavorites: () -> Unit,
    onRecycleBin: () -> Unit,
    onCreateAlbum: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickAccessChip(Icons.Default.Favorite, "Favorites", onClick = onFavorites, modifier = Modifier.weight(1f))
        QuickAccessChip(Icons.Default.Delete, "Recycle Bin", onClick = onRecycleBin, modifier = Modifier.weight(1f))
        QuickAccessChip(Icons.Default.CreateNewFolder, "New Album", onClick = onCreateAlbum, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuickAccessChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoveToAlbumSheet(
    albums: List<VaultAlbum>,
    currentAlbumId: String?,
    onAlbumSelected: (String?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        Text(
            "Move to Album",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        LazyColumn {
            item {
                ListItem(
                    headlineContent = { Text("None (Unorganized)") },
                    leadingContent = { Icon(Icons.Default.FolderOff, null) },
                    modifier = Modifier.combinedClickable(onClick = { onAlbumSelected(null) })
                )
            }
            items(albums) { album ->
                ListItem(
                    headlineContent = { Text(album.name) },
                    leadingContent = { 
                        Icon(
                            Icons.Default.Folder, 
                            null, 
                            tint = if (album.id == currentAlbumId) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        ) 
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = { if (album.id != currentAlbumId) onAlbumSelected(album.id) }
                    ),
                    trailingContent = {
                        if (album.id == currentAlbumId) {
                            Text("Current", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyVaultHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Text("Your vault is empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Tap the Import button to add photos and videos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}
