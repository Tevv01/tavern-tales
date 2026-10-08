package dev.tevv.taverntales.ui.info

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What the user is about to send, shown for review before it goes. */
private data class Draft(val message: String, val email: String?, val log: String?)

/**
 * Lets the user write a bug report and send it (to Sentry, as user feedback), or open a GitHub issue
 * instead. [canSend] is false in builds that can't report (debug); the form is shown but can't send.
 * [send] returns the report exactly as it was sent.
 */
@Composable
fun BugReportScreen(
    canSend: Boolean,
    onBack: () -> Unit,
    readLog: suspend () -> String,
    send: suspend (message: String, email: String?, log: String?) -> String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var includeLog by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf<String?>(null) }
    var showSent by remember { mutableStateOf(false) }
    val emailOk = BugReports.isValidEmail(email)

    InfoScaffold("Report a bug", onBack) {
        val report = sent
        if (report != null) {
            Section(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Thank you!", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 10.dp))
                }
                Text(
                    "Your report was sent. It helps make Tavern Tales better for every table.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { showSent = true }) { Text("See exactly what was sent") }
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        } else {
            Section("What went wrong?") {
                Text(
                    "Describe what happened and what you expected instead. Details help: which scene, what you " +
                        "tapped, and whether it was the sound or the lights.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(BugReports.MAX_MESSAGE) },
                    label = { Text("What happened?") },
                    minLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = {
                        if (message.length > BugReports.MAX_MESSAGE - 500) Text("${message.length} / ${BugReports.MAX_MESSAGE}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Your email (optional)") },
                    singleLine = true,
                    isError = !emailOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    supportingText = { Text(if (emailOk) "Only if you'd like a reply." else "That doesn't look like an email address.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { includeLog = !includeLog },
                ) {
                    Checkbox(checked = includeLog, onCheckedChange = { includeLog = it })
                    Column(Modifier.padding(start = 4.dp)) {
                        Text("Include the app's log", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Recent technical messages from Tavern Tales, such as Hue connection or playback " +
                                "problems. You'll see it before anything is sent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                if (!canSend) {
                    Text(
                        "This is a development (debug) build, which can't send reports. Use GitHub below, or the released app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        error = null
                        scope.launch {
                            val log = if (includeLog) readLog().ifBlank { null } else null
                            draft = Draft(message.trim(), email.trim().ifBlank { null }, log)
                        }
                    },
                    enabled = canSend && message.isNotBlank() && emailOk,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Review and send")
                }
            }
            Section("What gets sent") {
                Text(
                    "Your message, your email if you give one, and the log if you include it. Plus the same technical " +
                        "details as a crash report: the app version, your phone model and Android version, memory, " +
                        "storage, battery level, screen size, network type, language and time-zone settings, and the " +
                        "app's random install ID. It goes to Sentry, stored in the EU. No IP address, location, scenes, " +
                        "sounds or pictures.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Section("Prefer GitHub?") {
            Text(
                "With a GitHub account you can open a public issue instead, and follow along as it gets fixed. " +
                    "The app version and phone model are filled in for you.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = {
                    val version = runCatching {
                        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA).versionName
                    }.getOrNull() ?: "?"
                    val url = BugReports.githubIssueUrl(version, "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE)
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Open an issue on GitHub", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    draft?.let { pending ->
        AlertDialog(
            onDismissRequest = { if (!sending) draft = null },
            title = { Text("Send this report?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your message", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(pending.message, style = MaterialTheme.typography.bodyMedium)
                    pending.email?.let {
                        Text("Reply to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    pending.log?.let {
                        Text(
                            "App log (${it.lines().size} lines)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ReportText(it, Modifier.heightIn(max = 200.dp))
                    }
                    Text(
                        "Also sent: the app version, phone model and Android version, and technical details such as " +
                            "memory, storage and network type. After sending you can see the complete report.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !sending,
                    onClick = {
                        sending = true
                        scope.launch {
                            try {
                                sent = send(pending.message, pending.email, pending.log)
                                message = ""
                                email = ""
                                includeLog = false
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "The report couldn't be sent. Try again in a moment."
                            } finally {
                                sending = false
                                draft = null
                            }
                        }
                    },
                ) {
                    if (sending) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text("Send", modifier = Modifier.padding(start = 8.dp))
                }
            },
            dismissButton = { TextButton(enabled = !sending, onClick = { draft = null }) { Text("Cancel") } },
        )
    }

    if (showSent) {
        sent?.let {
            ReportDialog(
                title = "Report sent",
                intro = "This is everything that was sent, nothing more.",
                report = it,
                onDismiss = { showSent = false },
            )
        }
    }
}
