package com.vaultgallery.ui.screens.favorites

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
class FavoritesViewModel @Inject constructor(private val repository: VaultRepository) : ViewModel() {
    val favorites: StateFlow<List<VaultMedia>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenMedia: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val favorites by viewModel.favorites.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorites", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    Text("No favorites yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                items(favorites, key = { it.id }) { media ->
                    MediaThumbnailCard(
                        media = media,
                        isSelected = false,
                        isSelectionMode = false,
                        onClick = { onOpenMedia(media.id) },
                        onLongClick = { onOpenMedia(media.id) }
                    )
                }
            }
        }
    }
}
