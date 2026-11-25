package com.breckneck.debtbook.core.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.createGraph
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun PreparedScreenHost(
    navController: NavHostController,
    startDestination: Any,
    initialize: NavHostController.() -> Unit,
    animate: Boolean,
    tabBar: @Composable BoxScope.(NavBackStackEntry, MainTab) -> Unit,
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    builder: NavGraphBuilder.() -> Unit,
) {
    val graph = remember(navController, startDestination, builder) {
        navController.createGraph(startDestination, builder = builder)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    navController.setViewModelStore(checkNotNull(LocalViewModelStoreOwner.current).viewModelStore)
    val hadBackStack = navController.currentBackStackEntry != null
    navController.graph = graph
    if (!hadBackStack && navController.currentBackStackEntry != null) {
        // Restore the launch tab before creating the visual transition state.
        navController.initialize()
    }
    val navigator = navController.navigatorProvider.getNavigator(ScreenNavigator::class.java)
    DisposableEffect(navigator, lifecycleOwner) {
        navController.setLifecycleOwner(lifecycleOwner)
        onDispose(navigator::completeTransitions)
    }

    val stack by navigator.backStack.collectAsState()
    val visibleEntries by navController.visibleEntries.collectAsState()
    var preview by remember { mutableStateOf<BackPreview?>(null) }
    PredictiveBackHandler(enabled = stack.size > 1) { events ->
        val entries = navigator.backStack.value
        if (entries.size < 2) return@PredictiveBackHandler
        val gesture = BackPreview(entries.last(), entries[entries.lastIndex - 1])
        try {
            events.collect { event ->
                if (animate && navController.currentBackStackEntry == gesture.from) {
                    if (preview !== gesture) {
                        navigator.prepareForTransition(gesture.from)
                        navigator.prepareForTransition(gesture.to)
                        preview = gesture
                    }
                    gesture.progress = event.progress
                }
            }
            if (navController.currentBackStackEntry == gesture.from) navController.popBackStack()
        } finally {
            if (preview === gesture) preview = null
        }
    }

    val saveableState = rememberSaveableStateHolder()
    val entry = stack.lastOrNull() ?: return
    val gesture = preview?.takeIf { it.from == entry }
    val frames = remember { mutableStateMapOf<String, CompletableDeferred<Unit>>() }
    val zIndices = remember { mutableMapOf<String, Float>() }
    val state = remember { SeekableTransitionState(entry) }
    val transition = rememberTransition(state, label = "Screen navigation")
    LaunchedEffect(entry, gesture, animate) {
        if (gesture != null && animate) {
            snapshotFlow { gesture.progress }.collect { state.seekTo(it, gesture.to) }
            return@LaunchedEffect
        }

        when {
            !animate -> state.snapTo(entry)
            state.currentState != entry -> {
                if (state.targetState != entry || state.fraction == 0f) {
                    // Stage both screens at progress zero, then start the clock after placement.
                    state.seekTo(0f, entry)
                    snapshotFlow { frames[entry.id] }.filterNotNull().first().await()
                    withFrameNanos { }
                }
                state.animateTo(entry)
            }
            state.targetState != entry -> state.rewindPreview(transition.totalDurationNanos / 1_000_000)
        }
        if (navController.currentBackStackEntry == entry) {
            navigator.completeTransitions()
            zIndices.keys.retainAll(setOf(entry.id))
        }
    }

    val from = state.currentState
    val to = state.targetState
    val sharedTabBar = from.destination.mainTab() != null && to.destination.mainTab() != null
    // Keep one indicator across tab switches. For a stacked screen the panel belongs to the root layer.
    val tabEntry = if (sharedTabBar) {
        entry.takeIf { it.destination.mainTab() != null } ?: from
    } else {
        from.takeIf { it.destination.mainTab() != null } ?: to
    }
    val tab = tabEntry.destination.mainTab()
    Box(Modifier.fillMaxSize()) {
        transition.AnimatedContent(
            transitionSpec = {
                val pop = gesture != null || navigator.isPop
                val fromZ = zIndices[initialState.id] ?: 0f
                val toZ = when {
                    initialState.id == targetState.id -> fromZ
                    pop -> fromZ - 1f
                    else -> fromZ + 1f
                }
                zIndices[targetState.id] = toZ
                val moving = animate && initialState in visibleEntries
                ContentTransform(
                    targetContentEnter = when {
                        !moving -> EnterTransition.None
                        pop -> popEnterTransition()
                        else -> enterTransition()
                    },
                    initialContentExit = when {
                        !moving -> ExitTransition.None
                        pop -> popExitTransition()
                        else -> exitTransition()
                    },
                    targetContentZIndex = toZ,
                    sizeTransform = null,
                )
            },
            contentKey = { it.id },
        ) { screen ->
            if (screen in visibleEntries || screen == state.currentState || screen == state.targetState) {
                val firstLayout = remember(screen.id) { CompletableDeferred<Unit>() }
                val reportPlacement: () -> Unit = remember(firstLayout) {
                    { firstLayout.complete(Unit) }
                }
                DisposableEffect(firstLayout) {
                    frames[screen.id] = firstLayout
                    onDispose { frames.remove(screen.id) }
                }
                screen.LocalOwnersProvider(saveableState) {
                    (screen.destination as ScreenNavigator.Screen).content(screen, reportPlacement) {
                        if (!sharedTabBar && tab != null && screen == tabEntry) tabBar(screen, tab)
                    }
                }
            }
        }
        if (sharedTabBar && tab != null) tabBar(tabEntry, tab)
    }
}

private class BackPreview(val from: NavBackStackEntry, val to: NavBackStackEntry) {
    var progress by mutableFloatStateOf(0f)
}

private suspend fun SeekableTransitionState<NavBackStackEntry>.rewindPreview(durationMillis: Long) = coroutineScope {
    animate(fraction, 0f, animationSpec = tween((fraction * durationMillis).toInt())) { value, _ ->
        launch(start = CoroutineStart.UNDISPATCHED) { seekTo(value) }
    }
    snapTo(currentState)
}
