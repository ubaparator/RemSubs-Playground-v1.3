package com.example

import com.example.update.AppUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdaterTest {

    private val manifestUrl = "https://github.com/v6u1/RemSubs-Playground-v1.3/releases/latest/download/update.json"

    @Test
    fun manifestWrittenByCiIsParsed() {
        val json = """
            {"versionCode":142,"versionName":"1.3.142",
             "apkUrl":"https://github.com/v6u1/RemSubs-Playground-v1.3/releases/download/build-142/RemSubs.apk",
             "sha256":"9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08",
             "size":83261440,"notes":"feat: anime search"}
        """.trimIndent()
        val info = AppUpdater.parseManifest(json, manifestUrl)
        assertEquals(142L, info.versionCode)
        assertEquals("1.3.142", info.versionName)
        assertEquals("https://github.com/v6u1/RemSubs-Playground-v1.3/releases/download/build-142/RemSubs.apk", info.apkUrl)
        // Lowercased to match the hex digest computed on device
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", info.sha256)
        assertEquals(83261440L, info.sizeBytes)
        assertEquals("feat: anime search", info.notes)
    }

    @Test
    fun relativeApkUrlResolvesNextToTheManifest() {
        val info = AppUpdater.parseManifest("""{"versionCode":7,"apkUrl":"RemSubs.apk"}""", manifestUrl)
        assertEquals("https://github.com/v6u1/RemSubs-Playground-v1.3/releases/latest/download/RemSubs.apk", info.apkUrl)
        assertEquals("7", info.versionName)
        assertNull(info.sha256)
        assertEquals(-1L, info.sizeBytes)
    }
}
