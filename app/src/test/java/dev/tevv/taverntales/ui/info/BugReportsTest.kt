package dev.tevv.taverntales.ui.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class BugReportsTest {
    @Test
    fun emailIsOptionalButMustLookLikeAnAddress() {
        assertTrue(BugReports.isValidEmail(""))
        assertTrue(BugReports.isValidEmail("  "))
        assertTrue(BugReports.isValidEmail("player@example.com"))
        assertTrue(BugReports.isValidEmail(" player@example.com "))
        assertFalse(BugReports.isValidEmail("player"))
        assertFalse(BugReports.isValidEmail("player@example"))
        assertFalse(BugReports.isValidEmail("two words@example.com"))
    }

    @Test
    fun githubIssueUrlOpensANewIssueWithTheDetailsFilledIn() {
        val url = BugReports.githubIssueUrl("0.5.0", "Samsung SM-S928B", "16")
        val uri = URI(url) // throws if anything is left unencoded
        assertEquals("/Tevv01/tavern-tales/issues/new", uri.path)
        assertFalse("spaces must be %20, not +", url.contains("+"))
        val body = URLDecoder.decode(url.substringAfter("body="), "UTF-8")
        assertTrue(body.startsWith("**What happened?**"))
        assertTrue(body.endsWith("Tavern Tales 0.5.0 · Samsung SM-S928B · Android 16"))
    }
}
