package com.lito.planify.dashboard.updates

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GitHubUpdatesTest {
    private val metadata = """{"tag_name":"v1.10.0","draft":false,"prerelease":false,"assets":[
        {"name":"Planify-Dashboard.apk","size":3,"state":"uploaded"},{"name":"Planify-Dashboard.apk.sha256","size":80,"state":"uploaded"}]}"""

    @Test fun onlyOffersNewerCompleteStableReleasesForThisApplication() {
        val release = parseRelease(metadata)
        assertTrue(release.newerThan("1.9.9"))
        assertFalse(release.newerThan("1.10.0"))
        assertFalse(release.newerThan("2.0.0"))
        for (invalid in listOf(metadata.replace("false", "true"), metadata.replace("v1.10.0", "../test"),
            metadata.replace("Planify-Dashboard.apk", "Other.apk"), metadata.replace("uploaded", "open"))) {
            assertThrows(Exception::class.java) { parseRelease(invalid) }
        }
    }

    @Test fun rejectsCorruptTruncatedAndOversizedDownloads() {
        val sha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        fun download(text: String) = runBlocking {
            val output = ByteArrayOutputStream()
            copyVerifiedApk(text.byteInputStream(), output, sha, 3) {}
            output.toString("UTF-8")
        }
        assertEquals("abc", download("abc"))
        listOf("abcx", "ab", "axc").forEach { assertThrows(IllegalArgumentException::class.java) { download(it) } }
    }
}
