package app.burrow.money.data

import app.burrow.money.data.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the in-app updater reads from GitHub's "latest release" JSON and SHA256SUMS.txt. */
class ReleaseParseTest {
    private val repo = "https://github.com/nisabmohd/burrow"
    private fun asset(name: String) = """{"name":"$name","browser_download_url":"https://dl/$name"}"""
    private fun release(tag: String = "v0.2.0", body: String? = "- One\n- Two", vararg assets: String) =
        """{"tag_name":"$tag","html_url":"https://gh/rel/$tag","body":${body?.let { "\"" + it.replace("\n", "\\n") + "\"" } ?: "null"},"assets":[${assets.joinToString(",")}]}"""

    @Test fun picksTheGithubApkOverOthers() {
        val u = UpdateManager.parseRelease(release(assets = arrayOf(asset("burrow-0.2.0-foss.apk"), asset("burrow-0.2.0-github.apk"), asset("SHA256SUMS.txt"))), repo)
        assertEquals("0.2.0", u.version)
        assertEquals("burrow-0.2.0-github.apk", u.apkName)
        assertEquals("https://dl/burrow-0.2.0-github.apk", u.apkUrl)
        assertEquals("https://dl/SHA256SUMS.txt", u.checksumsUrl)
        assertEquals(listOf("One", "Two"), u.notes)
    }

    @Test fun neverPicksThePlayApk() {
        val u = UpdateManager.parseRelease(release(assets = arrayOf(asset("burrow-0.2.0-play.apk"))), repo)
        assertNull("no usable APK: the release page opens instead", u.apkUrl)
    }

    @Test fun fallsBackToAnyNonPlayApk() {
        val u = UpdateManager.parseRelease(release(assets = arrayOf(asset("burrow-0.2.0-play.apk"), asset("burrow-0.2.0-foss.apk"))), repo)
        assertEquals("burrow-0.2.0-foss.apk", u.apkName)
    }

    @Test fun tagWithoutVAndNullBodyAndNoAssets() {
        val u = UpdateManager.parseRelease(release(tag = "0.3.0", body = null), repo)
        assertEquals("0.3.0", u.version)
        assertEquals(emptyList<String>(), u.notes)
        assertNull(u.apkUrl); assertNull(u.checksumsUrl)
        assertEquals("https://gh/rel/0.3.0", u.pageUrl)
    }

    @Test fun missingPageUrlFallsBackToReleasesLatest() {
        val u = UpdateManager.parseRelease("""{"tag_name":"v1.0.0","assets":[]}""", repo)
        assertEquals("$repo/releases/latest", u.pageUrl)
    }

    @Test(expected = Exception::class) fun missingTagFails() {
        UpdateManager.parseRelease("""{"assets":[]}""", repo)
    }

    @Test fun shaLookupHandlesBothFormatsAndMisses() {
        val sums = "aaa111  burrow-0.2.0-foss.apk\nbbb222 *burrow-0.2.0-github.apk\n\n   ccc333   other.apk  \n"
        assertEquals("aaa111", UpdateManager.sha256For(sums, "burrow-0.2.0-foss.apk"))
        assertEquals("bbb222", UpdateManager.sha256For(sums, "burrow-0.2.0-github.apk"))
        assertEquals("ccc333", UpdateManager.sha256For(sums, "other.apk"))
        assertNull(UpdateManager.sha256For(sums, "burrow-0.2.0-play.apk"))
        assertNull(UpdateManager.sha256For("", "x.apk"))
        assertNull("a file name that only starts the same doesn't match", UpdateManager.sha256For("aaa  burrow.apk.old", "burrow.apk"))
    }
}
