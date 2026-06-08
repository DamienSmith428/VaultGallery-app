package com.vaultgallery.ui.screens.viewer

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaultgallery.data.repository.VaultRepository
import com.vaultgallery.domain.model.MediaType
import com.vaultgallery.domain.model.VaultMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.absoluteValue

data class ViewerState(
    val allMedia: List<VaultMedia> = emptyList(),
    val initialIndex: Int = 0,
    val currentMedia: VaultMedia? = null,
    val isLoading: Boolean = true,
    val exportMessage: String? = null
)

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository
) : ViewModel() {

    private val initialMediaId: String = savedStateHandle["mediaId"] ?: ""
    private val sourceContext: String = savedStateHandle["sourceContext"] ?: "ALL"
    private val albumId: String? = savedStateHandle["albumId"]
    private val searchQuery: String? = savedStateHandle["searchQuery"]

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val mediaFlow = when (sourceContext) {
                "ALBUM" -> repository.getMediaByAlbum(albumId ?: "")
                "FAVORITES" -> repository.getFavorites()
                "RECYCLE" -> repository.getRecycleBin()
                "SEARCH" -> if (searchQuery.isNullOrBlank()) repository.getAllMedia() else repository.searchMedia(searchQuery)
                else -> repository.getAllMedia()
            }

            mediaFlow.collect { media ->
                val idx = media.indexOfFirst { it.id == initialMediaId }.coerceAtLeast(0)
                _state.update { it.copy(allMedia = media, initialIndex = idx, isLoading = false, currentMedia = media.getOrNull(idx)) }
            }
        }
    }

    fun onPageChanged(index: Int) {
        _state.update { it.copy(currentMedia = it.allMedia.getOrNull(index)) }
    }

    fun toggleFavorite() {
        val media = _state.value.currentMedia ?: return
        viewModelScope.launch {
            repository.toggleFavorite(media.id, !media.isFavorite)
        }
    }

    fun moveToRecycleBin(onDone: () -> Unit) {
        val media = _state.value.currentMedia ?: return
        viewModelScope.launch {
            repository.moveToRecycleBin(media.id)
            onDone()
        }
    }

    fun exportToGallery() {
        val media = _state.value.currentMedia ?: return
        viewModelScope.launch {
            val success = repository.exportToGallery(media)
            _state.update { it.copy(exportMessage = if (success) "Saved to gallery" else "Failed to save") }
        }
    }

    fun clearExportMessage() = _state.update { it.copy(exportMessage = null) }

    suspend fun getDecryptedFile(encryptedFileName: String): ByteArray? = withContext(Dispatchers.IO) {
        repository.readDecryptedBytes(encryptedFileName)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    initialMediaId: String,
    onBack: () -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showUi by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.exportMessage) {
        state.exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearExportMessage()
        }
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.allMedia.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Media not found", color = Color.White)
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = state.initialIndex,
        pageCount = { state.allMedia.size }
    )
    
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                userScrollEnabled = true,
                flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
            ) { page ->
                val media = state.allMedia[page]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            alpha = (1f - pageOffset.absoluteValue).coerceIn(0f, 1f)
                            val scaleFactor = (1f - (pageOffset.absoluteValue * 0.15f)).coerceIn(0.85f, 1f)
                            scaleX = scaleFactor
                            scaleY = scaleFactor
                        }
                ) {
                    when (media.mediaType) {
                        MediaType.IMAGE -> ImageViewer(media = media, viewModel = viewModel, onClick = { showUi = !showUi })
                        MediaType.VIDEO -> VideoViewer(media = media, viewModel = viewModel, onClick = { showUi = !showUi })
                    }
                }
            }

            AnimatedVisibility(visible = showUi, enter = fadeIn(), exit = fadeOut()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .statusBarsPadding()
                            .align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            state.currentMedia?.let { media ->
                                IconButton(onClick = { viewModel.exportToGallery() }) {
                                    Icon(Icons.Default.Download, contentDescription = "Save to gallery", tint = Color.White)
                                }
                                IconButton(onClick = { viewModel.toggleFavorite() }) {
                                    Icon(
                                        if (media.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (media.isFavorite) Color.Red else Color.White
                                    )
                                }
                                IconButton(onClick = { viewModel.moveToRecycleBin { onBack() } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                                }
                            }
                        }
                    }

                    state.currentMedia?.let { media ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .navigationBarsPadding()
                                .padding(16.dp)
                                .align(Alignment.BottomCenter)
                        ) {
                            Text(media.originalFileName, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageViewer(
    media: VaultMedia,
    viewModel: MediaViewerViewModel,
    onClick: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    val animatedScale by animateFloatAsState(targetValue = scale, label = "scale")
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX, label = "offsetX")
    val animatedOffsetY by animateFloatAsState(targetValue = offsetY, label = "offsetY")

    val bytesState = produceState<ByteArray?>(initialValue = null, key1 = media.id) {
        value = viewModel.getDecryptedFile(media.encryptedFileName)
    }
    val bytes = bytesState.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .pointerInput(scale) {
                if (scale > 1f) {
                    detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offsetX += pan.x
                        offsetY += pan.y
                        if (scale <= 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                } else {
                    // Detect double tap even when scale is 1
                    detectTapGestures(onDoubleTap = { scale = 2.5f })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bytes != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(bytes)
                    .crossfade(true)
                    .build(),
                contentDescription = media.originalFileName,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = animatedScale,
                        scaleY = animatedScale,
                        translationX = animatedOffsetX,
                        translationY = animatedOffsetY
                    ),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun VideoViewer(
    media: VaultMedia,
    viewModel: MediaViewerViewModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val tempFileState = produceState<File?>(initialValue = null, key1 = media.id) {
        try {
            val bytes = viewModel.getDecryptedFile(media.encryptedFileName)
            if (bytes != null) {
                val tmp = File(context.cacheDir, "vaultplayer_${media.id}.tmp")
                tmp.writeBytes(bytes)
                value = tmp
            }
        } catch (e: Exception) {
            value = null
        }
    }
    val tempFile = tempFileState.value

    val exoPlayer = remember(tempFile) {
        if (tempFile == null) return@remember null
        ExoPlayer.Builder(context).build().also { player ->
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(tempFile)))
            player.prepare()
            player.playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
            tempFile?.delete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (tempFile == null) {
            CircularProgressIndicator(color = Color.White)
        } else if (exoPlayer != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Use clickable for tap toggle - very swipe-friendly
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VideocamOff, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                Text("Cannot play video", color = Color.Gray)
            }
        }
    }
}
