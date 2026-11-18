package io.github.highwindbr.aaxp.core

/**
 * The status bar page shows parts and the background the way people think of them ("Show clock",
 * "Show background"); the stored setting keeps the opposite (a hide list, noPlate). These
 * functions are the only place the two are converted.
 */
object StatusBarView {
    fun isShown(hidden: Set<String>, id: String): Boolean = id !in hidden

    /** The hide list after switching [id] to [shown]. */
    fun withShown(hidden: Set<String>, id: String, shown: Boolean): Set<String> = if (shown) hidden - id else hidden + id

    fun backgroundShown(noPlate: Boolean): Boolean = !noPlate

    fun noPlateFor(backgroundShown: Boolean): Boolean = !backgroundShown
}
