package dev.tevv.taverntales.ui.info

import java.net.URLEncoder

/** The pure parts of the bug report screen, kept here so they can be unit-tested. */
internal object BugReports {
    /** Sentry keeps feedback messages up to about 4096 characters. */
    const val MAX_MESSAGE = 4000

    private val email = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** True for an empty field (the email is optional) or something shaped like an address. */
    fun isValidEmail(text: String): Boolean = text.isBlank() || email.matches(text.trim())

    /** A new GitHub issue with the questions and the app and phone details filled in. */
    fun githubIssueUrl(appVersion: String, phone: String, android: String): String {
        val body = """
            |**What happened?**
            |
            |
            |**What did you expect to happen?**
            |
            |
            |---
            |Tavern Tales $appVersion · $phone · Android $android
        """.trimMargin()
        return "$REPO_URL/issues/new?body=" + URLEncoder.encode(body, "UTF-8").replace("+", "%20")
    }
}
