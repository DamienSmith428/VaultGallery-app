package com.vaultgallery.ui.screens.album

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.data.ImportResult
import com.vaultgallery.data.repository.VaultRepository
import com.vaultgallery.domain.model.MediaType
import com.vaultgallery.domain.model.VaultAlbum
import com.vaultgallery.domain.model.VaultMedia
import com.vaultgallery.data.security.AutoLockManager
import com.vaultgallery.ui.components.MediaThumbnailCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailState(
    val albumId: String = "",
    val albumName: String = "",
    val media: List<VaultMedia> = emptyList(),
    val albums: List<VaultAlbum> = emptyList(),
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val selectedIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val mediaToMove: VaultMedia? = null
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository
) : ViewModel() {

    private val albumId: String = savedStateHandle["albumId"] ?: ""

    private val _state = MutableStateFlow(AlbumDetailState(albumId = albumId))
    val state: StateFlow<AlbumDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getMediaByAlbum(albumId).collect { media ->
                _state.update { it.copy(media = media) }
            }
        }
        viewModelScope.launch {
            repository.getAllAlbums().collect { albums ->
                _state.update { it.copy(albums = albums) }
                albums.find { it.id == albumId }?.let { album ->
                    _state.update { it.copy(albumName = album.name) }
                }
            }
        }
    }

    fun importMedia(uris: List<Uri>) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true) }
            val results = repository.importMedia(uris, albumId)
            val ok = results.count { it is ImportResult.Success || it is ImportResult.PartialSuccess }
            _state.update { it.copy(isImporting = false, importMessage = "$ok item(s) imported") }
        }
    }

    fun clearMessage() = _state.update { it.copy(importMessage = null) }

    fun exportSelected() {
        viewModelScope.launch {
            val selectedMedia = _state.value.media.filter { it.id in _state.value.selectedIds }
            var successCount = 0
            selectedMedia.forEach { media ->
                if (repository.exportToGallery(media)) {
                    successCount++
                }
            }
            _state.update { it.copy(importMessage = "Successfully exported $successCount item(s)") }
            clearSelection()
        }
    }

    fun toggleSelection(id: String) {
        _state.update { s ->
            val set = s.selectedIds.toMutableSet()
            if (id in set) set.remove(id) else set.add(id)
            s.copy(selectedIds = set, isSelectionMode = set.isNotEmpty())
        }
    }
    
    fun clearSelection() = _state.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
    
    fun deleteSelected() {
        viewModelScope.launch {
            _state.value.selectedIds.forEach { repository.moveToRecycleBin(it) }
            clearSelection()
        }
    }

    fun setAsCover(mediaId: String) {
        viewModelScope.launch {
            repository.setAlbumCover(albumId, mediaId)
        }
    }

    fun setMediaToMove(media: VaultMedia?) {
        _state.update { it.copy(mediaToMove = media) }
    }

    fun moveToAlbum(targetAlbumId: String?) {
        val media = _state.value.mediaToMove ?: return
        viewModelScope.launch {
            repository.moveToAlbum(media.id, targetAlbumId)
            setMediaToMove(null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    onOpenMedia: (String) -> Unit,
    autoLockManager: AutoLockManager,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.importMedia(uris)
    }

    LaunchedEffect(state.importMessage) {
        state.importMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isSelectionMode) "${state.selectedIds.size} selected" else state.albumName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = if (state.isSelectionMode) viewModel::clearSelection else onBack) {
                        Icon(if (state.isSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
                        IconButton(onClick = viewModel::exportSelected) {
                            Icon(Icons.Default.Download, contentDescription = "Export selected")
                        }
                        IconButton(onClick = viewModel::deleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { 
                            autoLockManager.setTemporaryExiting(true)
                            picker.launch("*/*") 
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Import")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.media.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No media in this album", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.media, key = { it.id }) { media ->
                    MediaThumbnailCard(
                        media = media,
                        isSelected = media.id in state.selectedIds,
                        isSelectionMode = state.isSelectionMode,
                        onClick = {
                            if (state.isSelectionMode) viewModel.toggleSelection(media.id)
                            else onOpenMedia(media.id)
                        },
                        onLongClick = { viewModel.toggleSelection(media.id) },
                        onMove = { viewModel.setMediaToMove(media) },
                        additionalMenuItems = { dismiss ->
                            DropdownMenuItem(
                                text = { Text("Set as album cover") },
                                onClick = {
                                    dismiss()
                                    viewModel.setAsCover(media.id)
                                },
                                leadingIcon = { Icon(Icons.Default.Wallpaper, null) }
                            )
                        }
                    )
                }
            }
        }
    }

    if (state.mediaToMove != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setMediaToMove(null) },
            sheetState = sheetState
        ) {
            MoveToAlbumSheet(
                albums = state.albums,
                currentAlbumId = state.albumId,
                onAlbumSelected = viewModel::moveToAlbum
            )
        }
    }
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
