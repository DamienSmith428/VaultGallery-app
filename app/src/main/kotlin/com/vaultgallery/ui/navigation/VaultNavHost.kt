package com.vaultgallery.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vaultgallery.data.security.AutoLockManager
import com.vaultgallery.ui.screens.auth.LockScreen
import com.vaultgallery.ui.screens.home.HomeScreen
import com.vaultgallery.ui.screens.onboarding.OnboardingScreen
import com.vaultgallery.ui.screens.onboarding.OnboardingViewModel
import com.vaultgallery.ui.screens.album.AlbumDetailScreen
import com.vaultgallery.ui.screens.favorites.FavoritesScreen
import com.vaultgallery.ui.screens.recycle.RecycleBinScreen
import com.vaultgallery.ui.screens.search.SearchScreen
import com.vaultgallery.ui.screens.settings.SettingsScreen
import com.vaultgallery.ui.screens.viewer.MediaViewerScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val LOCK = "lock"
    const val HOME = "home"
    const val ALBUM_DETAIL = "album/{albumId}"
    const val MEDIA_VIEWER = "viewer/{mediaId}?sourceContext={sourceContext}&albumId={albumId}&searchQuery={searchQuery}"
    const val FAVORITES = "favorites"
    const val RECYCLE_BIN = "recycle_bin"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    fun albumDetail(albumId: String) = "album/$albumId"
    fun mediaViewer(mediaId: String, sourceContext: String = "ALL", albumId: String? = null, searchQuery: String? = null): String {
        var url = "viewer/$mediaId?sourceContext=$sourceContext"
        if (albumId != null) url += "&albumId=$albumId"
        if (searchQuery != null) url += "&searchQuery=$searchQuery"
        return url
    }
}

@Composable
fun VaultNavHost(autoLockManager: AutoLockManager) {
    val navController = rememberNavController()
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboardingState by onboardingViewModel.state.collectAsState()

    val isLocked by autoLockManager.isLocked.collectAsState()

    LaunchedEffect(isLocked) {
        if (isLocked) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != Routes.LOCK && currentRoute != Routes.ONBOARDING) {
                navController.navigate(Routes.LOCK) {
                    popUpTo(Routes.HOME) { inclusive = true }
                }
            }
        }
    }

    val startDestination = when {
        !onboardingState.hasCompletedOnboarding -> Routes.ONBOARDING
        else -> Routes.LOCK
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(400)
            ) + fadeIn(animationSpec = tween(400))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(400)
            ) + fadeOut(animationSpec = tween(400))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(400)
            ) + fadeIn(animationSpec = tween(400))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(400)
            ) + fadeOut(animationSpec = tween(400))
        }
    ) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.LOCK) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(
            Routes.LOCK,
            enterTransition = { fadeIn(animationSpec = tween(600)) },
            exitTransition = { fadeOut(animationSpec = tween(600)) }
        ) {
            LockScreen(
                onUnlocked = {
                    autoLockManager.unlock()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOCK) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenAlbum = { albumId -> navController.navigate(Routes.albumDetail(albumId)) },
                onOpenMedia = { mediaId -> navController.navigate(Routes.mediaViewer(mediaId, "ALL")) },
                onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                onOpenRecycleBin = { navController.navigate(Routes.RECYCLE_BIN) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                autoLockManager = autoLockManager
            )
        }

        composable(
            Routes.ALBUM_DETAIL,
            arguments = listOf(navArgument("albumId") { type = NavType.StringType })
        ) { backStack ->
            val albumId = backStack.arguments?.getString("albumId") ?: return@composable
            AlbumDetailScreen(
                albumId = albumId,
                onBack = { navController.popBackStack() },
                onOpenMedia = { mediaId -> navController.navigate(Routes.mediaViewer(mediaId, "ALBUM", albumId)) },
                autoLockManager = autoLockManager
            )
        }

        composable(
            Routes.MEDIA_VIEWER,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("sourceContext") { 
                    type = NavType.StringType
                    defaultValue = "ALL"
                },
                navArgument("albumId") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("searchQuery") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStack ->
            val mediaId = backStack.arguments?.getString("mediaId") ?: return@composable
            MediaViewerScreen(
                initialMediaId = mediaId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.FAVORITES) {
            FavoritesScreen(
                onBack = { navController.popBackStack() },
                onOpenMedia = { mediaId -> navController.navigate(Routes.mediaViewer(mediaId, "FAVORITES")) }
            )
        }

        composable(Routes.RECYCLE_BIN) {
            RecycleBinScreen(
                onBack = { navController.popBackStack() },
                onOpenMedia = { mediaId -> navController.navigate(Routes.mediaViewer(mediaId, "RECYCLE")) }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenMedia = { mediaId, query -> 
                    navController.navigate(Routes.mediaViewer(mediaId, "SEARCH", searchQuery = query)) 
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLockVault = {
                    autoLockManager.lockNow()
                }
            )
        }
    }
}
