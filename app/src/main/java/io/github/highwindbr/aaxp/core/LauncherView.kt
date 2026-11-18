package io.github.highwindbr.aaxp.core

/**
 * The App icons page says "Show app names"; the stored launcher setting is the opposite
 * (hideNames). These two functions are the only place the two are converted.
 */
object LauncherView {
    fun namesShown(hideNames: Boolean): Boolean = !hideNames

    fun hideNamesFor(namesShown: Boolean): Boolean = !namesShown
}
