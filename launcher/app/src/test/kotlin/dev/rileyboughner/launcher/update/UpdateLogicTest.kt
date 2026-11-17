package dev.rileyboughner.launcher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Exercises the two pieces of update logic that matter but need no Android
 * framework: JSON parsing of `version.json` (and that the committed file
 * itself parses to the current shipped version, so phones never prompt for
 * an update that doesn't exist yet) and the versionCode comparison that
 * decides whether to show the prompt.
 */
class UpdateLogicTest {

    @Test
    fun `committed version json parses to the current shipped version`() {
        val file = File("../version.json")
        assertTrue("expected launcher/version.json at ${file.absolutePath}", file.exists())

        val info = UpdateApi.parseUpdateInfo(file.readText())

        assertEquals(1, info.versionCode)
        assertEquals("0.1.0-skeleton", info.versionName)
        assertTrue(info.apkUrl.isNotBlank())
    }

    @Test
    fun `missing notes field defaults to empty string`() {
        val info = UpdateApi.parseUpdateInfo(
            """{"versionCode": 2, "versionName": "0.2.0", "apkUrl": "https://example.com/a.apk"}""",
        )
        assertEquals("", info.notes)
    }

    @Test
    fun `no update when remote matches current version code`() {
        assertFalse(isUpdateAvailable(remoteVersionCode = 1, currentVersionCode = 1, dismissedVersionCode = -1))
    }

    @Test
    fun `update available when remote is newer`() {
        assertTrue(isUpdateAvailable(remoteVersionCode = 2, currentVersionCode = 1, dismissedVersionCode = -1))
    }

    @Test
    fun `dismissed version does not prompt again`() {
        assertFalse(isUpdateAvailable(remoteVersionCode = 2, currentVersionCode = 1, dismissedVersionCode = 2))
    }

    @Test
    fun `a newer version than the dismissed one prompts again`() {
        assertTrue(isUpdateAvailable(remoteVersionCode = 3, currentVersionCode = 1, dismissedVersionCode = 2))
    }
}
