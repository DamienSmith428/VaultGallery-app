package com.vaultgallery.ui.screens.recycle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.data.repository.VaultRepository
import com.vaultgallery.domain.model.MediaType
import com.vaultgallery.domain.model.VaultMedia
import com.vaultgallery.ui.components.MediaThumbnailCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecycleBinViewModel @Inject constructor(private val repository: VaultRepository) : ViewModel() {
    val recycleBin: StateFlow<List<VaultMedia>> = repository.getRecycleBin()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(id: String) { viewModelScope.launch { repository.restoreFromRecycleBin(id) } }
    fun permanentlyDelete(media: VaultMedia) { viewModelScope.launch { repository.permanentlyDelete(media) } }
    fun emptyBin(media: List<VaultMedia>) { viewModelScope.launch { media.forEach { repository.permanentlyDelete(it) } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    onOpenMedia: (String) -> Unit,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val items by viewModel.recycleBin.collectAsState()
    var selectedItem by remember { mutableStateOf<VaultMedia?>(null) }
    var showEmptyConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { showEmptyConfirm = true }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Empty Bin", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    Text("Recycle bin is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { media ->
                    MediaThumbnailCard(
                        media = media,
                        isSelected = false,
                        isSelectionMode = false,
                        onClick = { onOpenMedia(media.id) },
                        onLongClick = { selectedItem = media },
                        onRestore = { viewModel.restore(media.id) },
                        onDelete = { viewModel.permanentlyDelete(media) }
                    )
                }
            }
        }
    }

    selectedItem?.let { media ->
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text(media.originalFileName) },
            text = { Text("What would you like to do with this item?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restore(media.id)
                    selectedItem = null
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.permanentlyDelete(media)
                        selectedItem = null
                    }
                ) { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text("Empty Recycle Bin") },
            text = { Text("This will permanently delete all ${items.size} items. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.emptyBin(items); showEmptyConfirm = false }) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
