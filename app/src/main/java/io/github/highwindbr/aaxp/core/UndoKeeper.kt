package io.github.highwindbr.aaxp.core

import java.io.File

/** The single saved copy of the look before the last change, kept in one file. */
class UndoKeeper(private val file: File) {
    fun exists(): Boolean = file.isFile

    fun load(): LookSnapshot? = if (file.isFile) runCatching { LookUndo.decode(file.readText()) }.getOrNull() else null

    fun clear() { file.delete() }

    /**
     * Builds and stores a new copy. If anything fails the old copy is removed too, so an undo
     * can never jump back past more than one change under an old label. Returns whether a copy was stored.
     */
    fun capture(build: () -> LookSnapshot): Boolean = try {
        file.writeText(LookUndo.encode(build()))
        true
    } catch (e: Exception) {
        file.delete()
        false
    }
}
