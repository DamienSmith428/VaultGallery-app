package com.vaultgallery.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.data.ImportResult
import com.vaultgallery.data.repository.VaultRepository
import com.vaultgallery.domain.model.VaultAlbum
import com.vaultgallery.domain.model.VaultMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val allMedia: List<VaultMedia> = emptyList(),
    val albums: List<VaultAlbum> = emptyList(),
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val selectedMediaIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val showCreateAlbumDialog: Boolean = false,
    val newAlbumName: String = "",
    val albumToRename: VaultAlbum? = null,
    val renameAlbumName: String = "",
    val mediaToMove: VaultMedia? = null,
    val totalCount: Int = 0,
    val totalSize: Long = 0L,
    val vaultSizeLimitGb: Int = 5,
    val showSizeLimitDialog: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getAllMedia(),
                repository.getAllAlbums(),
                repository.settings
            ) { media, albums, settings -> 
                _state.update { it.copy(
                    allMedia = media, 
                    albums = albums,
                    totalCount = media.size,
                    totalSize = media.sumOf { m -> m.size },
                    vaultSizeLimitGb = settings.vaultSizeLimitGb
                ) }
            }.collect()
        }
    }

    fun importMedia(uris: List<Uri>, albumId: String? = null) {
        viewModelScope.launch {
            val settings = repository.settings.first()
            val limitBytes = settings.vaultSizeLimitGb * 1024L * 1024L * 1024L
            if (_state.value.totalSize >= limitBytes) {
                _state.update { it.copy(importMessage = "Vault is full. Increase the size limit to add more media.") }
                return@launch
            }

            _state.update { it.copy(isImporting = true, importMessage = null) }
            val results = repository.importMedia(uris, albumId)
            val succeeded = results.count { it is ImportResult.Success || it is ImportResult.PartialSuccess }
            val failed = results.count { it is ImportResult.Failure }
            val partialCount = results.count { it is ImportResult.PartialSuccess }

            val msg = buildString {
                append("$succeeded item(s) imported")
                if (partialCount > 0) append(" ($partialCount not removed from gallery — manual deletion required)")
                if (failed > 0) append(", $failed failed")
            }
            _state.update { it.copy(isImporting = false, importMessage = msg) }
        }
    }

    fun clearImportMessage() = _state.update { it.copy(importMessage = null) }

    fun exportSelected() {
        viewModelScope.launch {
            val selectedMedia = _state.value.allMedia.filter { it.id in _state.value.selectedMediaIds }
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
            val newSet = s.selectedMediaIds.toMutableSet()
            if (id in newSet) newSet.remove(id) else newSet.add(id)
            s.copy(selectedMediaIds = newSet, isSelectionMode = newSet.isNotEmpty())
        }
    }

    fun clearSelection() = _state.update { it.copy(selectedMediaIds = emptySet(), isSelectionMode = false) }

    fun moveSelectedToRecycleBin() {
        viewModelScope.launch {
            _state.value.selectedMediaIds.forEach { repository.moveToRecycleBin(it) }
            clearSelection()
        }
    }

    fun setShowCreateAlbum(show: Boolean) = _state.update { it.copy(showCreateAlbumDialog = show, newAlbumName = "") }
    fun setNewAlbumName(name: String) = _state.update { it.copy(newAlbumName = name) }

    fun setShowSizeLimitDialog(show: Boolean) = _state.update { it.copy(showSizeLimitDialog = show) }

    fun updateVaultSizeLimit(limitGb: Int) {
        viewModelScope.launch {
            val currentSettings = repository.settings.first()
            repository.updateSettings(currentSettings.copy(vaultSizeLimitGb = limitGb))
            setShowSizeLimitDialog(false)
        }
    }

    fun createAlbum() {
        viewModelScope.launch {
            val name = _state.value.newAlbumName.trim()
            if (name.isNotEmpty()) {
                repository.createAlbum(name)
            }
            _state.update { it.copy(showCreateAlbumDialog = false, newAlbumName = "") }
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch { repository.deleteAlbum(albumId) }
    }

    fun setAlbumToRename(album: VaultAlbum?) {
        _state.update { it.copy(albumToRename = album, renameAlbumName = album?.name ?: "") }
    }

    fun setRenameAlbumName(name: String) {
        _state.update { it.copy(renameAlbumName = name) }
    }

    fun renameAlbum() {
        val album = _state.value.albumToRename ?: return
        val newName = _state.value.renameAlbumName.trim()
        if (newName.isNotEmpty() && newName != album.name) {
            viewModelScope.launch {
                repository.renameAlbum(album.id, newName)
            }
        }
        setAlbumToRename(null)
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
