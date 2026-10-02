package com.example

import com.example.torrent.MagnetLinks
import com.example.torrent.TorrentDownloadManager
import com.example.torrent.TorrentErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetLinksTest {

    @Test
    fun base32InfoHashFromSubsPleaseIsConvertedToHex() {
        // Real SubsPlease magnet (Frieren S2 01-10, 480p batch); expected value from Python's base64.b32decode
        val magnet = "magnet:?xt=urn:btih:VLJ3XGIWPFSZPFC3WRVIL2UCEQFHAW2B&dn=%5BSubsPlease%5D%20Test&xl=3895551825"
        assertEquals("aad3bb9916796597945bb46a85ea82240a705b41", MagnetLinks.infoHashHex(magnet))
    }

    @Test
    fun hexInfoHashIsLowercased() {
        val magnet = "magnet:?xt=urn:btih:EF3E7AD1B12BDD9FC341691D8866CD1FA8374A4B&dn=x"
        assertEquals("ef3e7ad1b12bdd9fc341691d8866cd1fa8374a4b", MagnetLinks.infoHashHex(magnet))
    }

    @Test
    fun invalidInfoHashesAreRejected() {
        assertNull(MagnetLinks.infoHashHex("magnet:?xt=urn:btih:ABC"))
        // '1' and '8' are not part of the Base32 alphabet
        assertNull(MagnetLinks.infoHashHex("magnet:?xt=urn:btih:1LJ3XGIWPFSZPFC3WRVIL2UCEQFHAW28"))
        // 40 chars but not hex
        assertNull(MagnetLinks.infoHashHex("magnet:?xt=urn:btih:ZZ3E7AD1B12BDD9FC341691D8866CD1FA8374A4B"))
        assertNull(MagnetLinks.infoHashHex("magnet:?dn=no-hash"))
    }

    @Test
    fun pastedMagnetIsCleanedUp() {
        val pasted = "  magnet:?xt=urn:btih:ef3e7ad1b12bdd9fc341691d8866cd1fa8374a4b&amp;dn=Show%20-%2001" +
            "&amp;tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce \n"
        val clean = MagnetLinks.normalize(pasted)
        assertTrue(MagnetLinks.isMagnet(clean))
        assertFalse(clean.contains("&amp;"))
        assertFalse(clean.contains(" "))
        assertEquals("Show - 01", MagnetLinks.displayName(clean))
    }

    @Test
    fun exactLengthIsRead() {
        val magnet = "magnet:?xt=urn:btih:M7TFKY5L64IYJGIKKTENHIIR4NE52ZZP&xl=1457009569&dn=a"
        assertEquals(1457009569L, MagnetLinks.exactLength(magnet))
        assertNull(MagnetLinks.exactLength("magnet:?xt=urn:btih:M7TFKY5L64IYJGIKKTENHIIR4NE52ZZP"))
    }

    @Test
    fun builtMagnetRoundTrips() {
        val title = "[Erai-raws] Show - 01 [1080p][Multiple Subtitle].mkv"
        val magnet = MagnetLinks.build("EF3E7AD1B12BDD9FC341691D8866CD1FA8374A4B", title)
        assertEquals("ef3e7ad1b12bdd9fc341691d8866cd1fa8374a4b", MagnetLinks.infoHashHex(magnet))
        assertEquals(title, MagnetLinks.displayName(magnet))
        assertEquals(MagnetLinks.PUBLIC_TRACKERS.size, Regex("&tr=").findAll(magnet).count())
        // Spaces must be %20; a '+' would stay a literal plus in magnet parsers
        assertFalse(magnet.contains("+"))
    }

    @Test
    fun onlyMagnetLinksAreAccepted() {
        assertTrue(MagnetLinks.isMagnet("MAGNET:?xt=urn:btih:ef3e7ad1b12bdd9fc341691d8866cd1fa8374a4b"))
        assertFalse(MagnetLinks.isMagnet("https://nyaa.si/view/2160092"))
        assertFalse(MagnetLinks.isMagnet(""))
    }

    @Test
    fun staleHandleErrorIsNotReportedAsCorruptTorrent() {
        // jlibtorrent 2.0.11's download(ti, ...) failed with exactly this for every .torrent file
        val (type, _) = TorrentDownloadManager.classifyErrorMessage("invalid torrent handle used")
        assertNotEquals(TorrentErrorType.INVALID_TORRENT, type)
    }

    @Test
    fun parserErrorsAreClassified() {
        assertEquals(
            TorrentErrorType.INVALID_TORRENT,
            TorrentDownloadManager.classifyErrorMessage("Can't decode data: invalid value").first
        )
        assertEquals(
            TorrentErrorType.INVALID_MAGNET,
            TorrentDownloadManager.classifyError(
                IllegalArgumentException("invalid magnet link: Invalid magnet uri: invalid tracker url")
            ).first
        )
    }
}
