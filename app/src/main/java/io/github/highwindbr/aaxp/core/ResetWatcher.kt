package io.github.highwindbr.aaxp.core

/**
 * A page menu's "reset" is a counter that goes up each time it is used. A page must bring its rows
 * back to default only when the counter changes while the page is open, not when it is opened
 * with a counter that is already above zero from an earlier reset.
 */
class ResetWatcher(initial: Int) {
    private var seen = initial

    /** True once for each change of [current] since the watcher was made or last asked. */
    fun changed(current: Int): Boolean {
        if (current == seen) return false
        seen = current
        return true
    }
}
