package dev.rileyboughner.launcher.moviewheel

data class Movie(
    val id: String,
    val title: String,
    val year: Int?,
    val genres: List<String>,
)

sealed interface MovieWheelResult<out T> {
    data class Success<T>(val value: T) : MovieWheelResult<T>
    data class Error(val message: String) : MovieWheelResult<Nothing>
}
