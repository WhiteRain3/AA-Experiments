package io.github.highwindbr.aaxp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private fun release(tag: String, url: String, name: String = "AA-Experiments.apk", extra: String = "") =
        """{"tag_name":"$tag","body":"notes"$extra,"assets":[{"name":"$name","size":1000,"browser_download_url":"$url"}]}"""
    private val good = "https://github.com/WhiteRain3/AA-Experiments/releases/download/1.0.0-wr17/AA-Experiments.apk"

    @Test fun buildNumbers() {
        assertEquals(17, UpdateCheck.buildNumber("1.0.0-wr17"))
        assertNull(UpdateCheck.buildNumber("1.0.0"))
        assertNull(UpdateCheck.buildNumber("1.0.0-wr17-debug"))
        assertNull(UpdateCheck.buildNumber(null))
    }

    @Test fun parsesAReleaseFromThisRepository() {
        val update = UpdateCheck.parse(release("1.0.0-wr17", good))!!
        assertEquals("1.0.0-wr17", update.versionName)
        assertEquals(17, update.build)
        assertEquals(good, update.apkUrl)
        assertEquals("notes", update.notes)
    }

    @Test fun refusesDownloadsFromAnywhereElse() {
        assertNull(UpdateCheck.parse(release("1.0.0-wr17", "https://example.com/x.apk")))
        assertNull(UpdateCheck.parse(release("1.0.0-wr17", "http://github.com/WhiteRain3/AA-Experiments/releases/download/a/b.apk")))
        assertNull(UpdateCheck.parse(release("1.0.0-wr17", "https://github.com/Someone/AA-Experiments/releases/download/a/b.apk")))
        assertNull(UpdateCheck.parse(release("1.0.0-wr17", good, name = "notes.txt")))
    }

    @Test fun ignoresDraftsPrereleasesAndOtherTags() {
        assertNull(UpdateCheck.parse(release("1.0.0-wr17", good, extra = ""","draft":true""")))
        assertNull(UpdateCheck.parse(release("1.0.0-wr17", good, extra = ""","prerelease":true""")))
        assertNull(UpdateCheck.parse(release("1.0.0", good)))
        assertNull(UpdateCheck.parse("not json"))
        assertNull(UpdateCheck.parse("{}"))
    }

    @Test fun newerOnlyWhenTheBuildNumberIsHigher() {
        val update = UpdateCheck.parse(release("1.0.0-wr17", good))!!
        assertTrue(UpdateCheck.isNewer(update, "1.0.0-wr16"))
        assertFalse(UpdateCheck.isNewer(update, "1.0.0-wr17"))
        assertFalse(UpdateCheck.isNewer(update, "1.0.0-wr18"))
        assertTrue(UpdateCheck.isNewer(update, "1.0.0"))
    }
}
