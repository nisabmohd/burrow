package app.burrow.money.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test fun newer() {
        assertTrue(isNewerVersion("v1.10.0", "1.9.3"))
        assertTrue(isNewerVersion("v0.1.2", "0.1.1-github"))
        assertTrue(isNewerVersion("2", "1.9.9"))
        assertFalse(isNewerVersion("v0.1.1", "0.1.1-github"))
        assertFalse(isNewerVersion("v0.0.9", "0.1.0"))
        assertFalse(isNewerVersion("0.1", "0.1.0"))
    }

    @Test fun highlights() {
        val body = "## What's new\n- **Coming up** tab\n* Credits from every bank\nnot a bullet\n- `four`\n- five\n- six"
        assertEquals(listOf("Coming up tab", "Credits from every bank", "four", "five", "six"), releaseHighlights(body))
        assertEquals(listOf("Coming up tab"), releaseHighlights(body, max = 1))
        assertEquals(emptyList<String>(), releaseHighlights(null))
    }
}
