package dev.rileyboughner.launcher.moviewheel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Orchestrates movie-wheel calls for the TV movie-wheel screen: runs network
 * I/O off the main thread and turns every failure into a
 * [MovieWheelResult.Error] — this must never let an exception reach the UI
 * and crash the launcher.
 */
class MovieWheelRepository {

    suspend fun fetchMovies(): MovieWheelResult<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            MovieWheelResult.Success(MovieWheelApi.fetchMovies())
        } catch (e: IOException) {
            MovieWheelResult.Error("Couldn't reach the movie wheel. Check your connection and try again.")
        } catch (e: Exception) {
            MovieWheelResult.Error("Couldn't load the movie wheel.")
        }
    }

    fun posterUrl(id: String): String = MovieWheelApi.posterUrl(id)
}
