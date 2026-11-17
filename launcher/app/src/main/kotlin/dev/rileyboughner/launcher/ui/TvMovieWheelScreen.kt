package dev.rileyboughner.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dev.rileyboughner.launcher.moviewheel.Movie
import dev.rileyboughner.launcher.moviewheel.MovieWheelRepository
import dev.rileyboughner.launcher.moviewheel.MovieWheelResult
import dev.rileyboughner.launcher.resolveLeanbackTile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val JELLYFIN_TV_PACKAGE_NAME = "org.jellyfin.androidtv"
private const val SPIN_MIN_DURATION_MS = 2600L
private const val SPIN_STEP_MS = 110L

private sealed interface WheelPhase {
    data object Idle : WheelPhase
    data class Spinning(val cyclingTitle: String) : WheelPhase
    data class Picked(val movie: Movie) : WheelPhase
}

/**
 * TV-native movie-wheel spin screen: fetches the real movie list once on
 * open (same list the web app uses), then picks uniformly at random in
 * Kotlin on each spin — there is no server-side spin/random endpoint to call
 * instead. D-pad only: initial focus on a big "Spin" tile, CENTER/OK spins
 * with a few seconds of cycling titles before settling on the pick.
 */
@Composable
fun TvMovieWheelScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val repository = remember { MovieWheelRepository() }
    val scope = rememberCoroutineScope()
    val currentOnBack = rememberUpdatedState(onBack)
    BackHandler(onBack = { currentOnBack.value() })

    var result by remember { mutableStateOf<MovieWheelResult<List<Movie>>?>(null) }

    suspend fun load() {
        result = null
        result = repository.fetchMovies()
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding(),
    ) {
        TvTextRow(
            label = "← TV Home",
            onClick = { currentOnBack.value() },
            modifier = Modifier.padding(horizontal = 56.dp),
        )
        HairlineDivider()

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (val r = result) {
                null -> CenteredMessage("Loading the movie wheel…")
                is MovieWheelResult.Error -> CenteredRetry(
                    message = r.message,
                    onRetry = { scope.launch { load() } },
                )
                is MovieWheelResult.Success -> {
                    if (r.value.isEmpty()) {
                        CenteredMessage("No movies in the wheel yet.")
                    } else {
                        MovieWheelContent(movies = r.value, repository = repository)
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieWheelContent(
    movies: List<Movie>,
    repository: MovieWheelRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val genres = remember(movies) { listOf("All") + movies.flatMap { it.genres }.distinct().sorted() }
    var genreIndex by remember { mutableStateOf(0) }
    val filteredMovies = remember(movies, genreIndex) {
        if (genreIndex == 0) movies else movies.filter { genres[genreIndex] in it.genres }
    }

    var phase by remember { mutableStateOf<WheelPhase>(WheelPhase.Idle) }
    val spinFocusRequester = remember { FocusRequester() }
    val spinAgainFocusRequester = remember { FocusRequester() }

    fun spin() {
        val pool = filteredMovies.ifEmpty { movies }
        phase = WheelPhase.Spinning(pool.random().title)
        scope.launch {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < SPIN_MIN_DURATION_MS) {
                phase = WheelPhase.Spinning(pool.random().title)
                delay(SPIN_STEP_MS)
            }
            phase = WheelPhase.Picked(pool.random())
        }
    }

    LaunchedEffect(Unit) { spinFocusRequester.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val p = phase) {
            is WheelPhase.Idle -> {
                if (genres.size > 1) {
                    GenreFilterRow(
                        genres = genres,
                        selectedIndex = genreIndex,
                        onSelectIndex = { genreIndex = it },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
                Text(
                    text = "Press OK to spin",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(24.dp))
                TvTile(
                    label = "Spin",
                    onClick = { spin() },
                    focusRequester = spinFocusRequester,
                    width = 240.dp,
                    height = 240.dp,
                    cornerRadius = 120.dp,
                ) {
                    Text(text = "🎡", style = MaterialTheme.typography.displayLarge)
                }
            }

            is WheelPhase.Spinning -> {
                SpinningVisual()
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = p.cyclingTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            is WheelPhase.Picked -> {
                PosterArt(posterUrl = repository.posterUrl(p.movie.id), title = p.movie.title)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = p.movie.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                if (p.movie.year != null) {
                    Text(
                        text = p.movie.year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    TvTile(
                        label = "Spin again",
                        onClick = { spin() },
                        focusRequester = spinAgainFocusRequester,
                        width = 170.dp,
                        height = 96.dp,
                        cornerRadius = 12.dp,
                    ) {
                        Text(text = "🎡", style = MaterialTheme.typography.displaySmall)
                    }
                    val jellyfinTile = remember { resolveLeanbackTile(context, JELLYFIN_TV_PACKAGE_NAME, "Jellyfin") }
                    if (jellyfinTile != null) {
                        TvTile(
                            label = "Open Jellyfin",
                            onClick = { context.startActivity(jellyfinTile.launchIntent) },
                            width = 170.dp,
                            height = 96.dp,
                            cornerRadius = 12.dp,
                        ) {
                            TvTileDrawableArtwork(jellyfinTile.artwork, contentDescription = "Jellyfin")
                        }
                    }
                }
                LaunchedEffect(p.movie.id) { spinAgainFocusRequester.requestFocus() }
            }
        }
    }
}

@Composable
private fun SpinningVisual(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wheelSpin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700, easing = LinearEasing), RepeatMode.Restart),
        label = "wheelSpinAngle",
    )
    Text(
        text = "🎡",
        style = MaterialTheme.typography.displayLarge,
        modifier = modifier.rotate(angle),
    )
}

@Composable
private fun PosterArt(posterUrl: String, title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 240.dp, height = 360.dp)
            .background(Color(0xFF141414)),
    ) {
        SubcomposeAsyncImage(
            model = posterUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            loading = { PosterPlaceholder() },
            error = { PosterPlaceholder() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PosterPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "🎬", style = MaterialTheme.typography.displayMedium, color = Color.White.copy(alpha = 0.4f))
    }
}

/** Left/right-cycles the genre filter on a focused row — D-pad simple, no text entry. */
@Composable
private fun GenreFilterRow(
    genres: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSelectIndex((selectedIndex - 1 + genres.size) % genres.size)
                        true
                    }
                    Key.DirectionRight -> {
                        onSelectIndex((selectedIndex + 1) % genres.size)
                        true
                    }
                    else -> false
                }
            }
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = "◂  Genre: ${genres[selectedIndex]}  ▸",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun CenteredMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun CenteredRetry(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val retryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocusRequester.requestFocus() }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Couldn't reach the movie wheel",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(24.dp))
            TvTile(
                label = "Retry",
                onClick = onRetry,
                focusRequester = retryFocusRequester,
                width = 170.dp,
                height = 96.dp,
                cornerRadius = 12.dp,
            ) {
                Text(text = "↻", style = MaterialTheme.typography.displaySmall, color = Color.White)
            }
        }
    }
}
