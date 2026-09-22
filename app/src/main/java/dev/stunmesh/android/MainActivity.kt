package dev.stunmesh.android

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.stunmesh.android.backend.BackendState
import dev.stunmesh.android.config.*
import dev.stunmesh.android.tunnel.TunnelManager
import dev.stunmesh.android.ui.theme.StunmeshTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mobile.Mobile

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The only secret input is an optional PSK, never a private-key editor.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { StunmeshTheme { ServerScreen() } }
    }
}

@Composable
private fun ServerScreen() {
    val context = LocalContext.current
    val repository = remember { ConfigRepository.get(context) }
    val store by repository.state.collectAsState()
    val status by TunnelManager.status.collectAsState()
    val backendState by TunnelManager.state.collectAsState()
    val runningId by TunnelManager.activeTunnelId.collectAsState()
    val authenticated by TunnelManager.authenticated.collectAsState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var enrollment by remember { mutableStateOf<Enrollment?>(null) }
    var psk by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingId by remember { mutableStateOf("") }
    var connectReview by remember { mutableStateOf<PublicTunnel?>(null) }
    var deletion by remember { mutableStateOf<PublicTunnel?>(null) }
    LaunchedEffect(repository) { withContext(Dispatchers.IO) { repository.load() } }

    fun runWork(block: () -> Unit) {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (_: Throwable) {
                notice =
                    "Operation failed. Existing configuration was preserved; check the selected inputs and device state."
            } finally {
                busy = false
            }
        }
    }
    fun activate() {
        val id = pendingId
        pendingId = ""
        if (id.isNotEmpty()) runWork { TunnelManager.start(context, id) }
    }
    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            activate()
        }
    fun afterConsent() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else activate()
    }
    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            if (result.resultCode == Activity.RESULT_OK) afterConsent()
            else {
                pendingId = ""
                notice = "VPN permission was not granted."
            }
        }
    fun connect(t: PublicTunnel) {
        pendingId = t.id
        val request = VpnService.prepare(context)
        if (request == null) afterConsent() else consent.launch(request)
    }
    fun review(text: String) {
        scope.launch {
            busy = true
            try {
                val candidate =
                    withContext(Dispatchers.Default) {
                        Provisioning.decode(text).also {
                            it.publicConfig.peers.forEach { p ->
                                Mobile.validatePublicKey(p.publicKey)
                            }
                        }
                    }
                enrollment = candidate
                psk = ""
                notice = ""
                input = ""
            } catch (_: Throwable) {
                notice =
                    "Public configuration rejected: check schema, keys and narrow routes. Private keys and secret blobs cannot be imported."
            } finally {
                busy = false
            }
        }
    }
    val importFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    busy = true
                    try {
                        val text =
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use {
                                    it.readBounded().toString(Charsets.UTF_8)
                                } ?: error("missing input")
                            }
                        review(text)
                    } catch (_: Throwable) {
                        notice = "Cannot read this public profile, or it exceeds 256 KiB."
                    } finally {
                        busy = false
                    }
                }
        }
    fun copyPublic(text: String) {
        context
            .getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Public STUNMESH enrollment", text))
        notice = "Public information copied."
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Server VPN", style = MaterialTheme.typography.headlineMedium)
            Text(status)
            Text(
                "Only the listed server IPs use the tunnel. Other traffic keeps using the phone's internet. Leave “Block connections without VPN” off."
            )
            if (notice.isNotEmpty()) Text(notice, color = MaterialTheme.colorScheme.primary)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (val current = store) {
                RepositoryState.Loading -> Text("Opening protected configuration…")
                RepositoryState.Unavailable -> {
                    Text(
                        "Configuration could not be read. Unlock the device and retry. Unreadable data is retained; it has not been replaced."
                    )
                    Button(onClick = { runWork { repository.load() } }, enabled = !busy) {
                        Text("Retry")
                    }
                }
                is RepositoryState.Ready -> {
                    current.profiles.forEach { t ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(t.name, style = MaterialTheme.typography.titleLarge)
                                Text("Server routes: ${t.routes.joinToString()}")
                                Text(
                                    "Phone public key",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(t.publicKey, fontFamily = FontFamily.Monospace)
                                t.serverKeys.forEach { key ->
                                    authenticated[key]
                                        ?.takeIf { runningId == t.id }
                                        ?.let { time ->
                                            Text(
                                                "Authenticated session observed: ${DateFormat.getDateTimeInstance().format(Date(time))}"
                                            )
                                        }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !busy,
                                        onClick = {
                                            if (
                                                runningId == t.id &&
                                                    backendState != BackendState.DOWN
                                            )
                                                runWork { TunnelManager.stop(context) }
                                            else connectReview = t
                                        },
                                    ) {
                                        Text(
                                            if (
                                                runningId == t.id &&
                                                    backendState != BackendState.DOWN
                                            )
                                                "Stop"
                                            else "Review & connect"
                                        )
                                    }
                                    TextButton(onClick = { copyPublic(Provisioning.response(t)) }) {
                                        Text("Copy public reply")
                                    }
                                }
                                TextButton(
                                    enabled = !busy && runningId != t.id,
                                    onClick = { deletion = t },
                                ) {
                                    Text("Remove profile")
                                }
                                Text(
                                    "Profiles are read-only to preserve all peer/store settings. Enroll a new profile for configuration changes; the new key needs server approval.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Add a device", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Scan the owner's QR with GrapheneOS Camera, then paste its public text here. Compare the server key and routes before saving."
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            if (it.length <= StrictDocument.MAX_BYTES) input = it
                            else notice = "Input exceeds 256 KiB."
                        },
                        label = { Text("Public enrollment text") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && input.isNotBlank(), onClick = { review(input) }) {
                            Text("Review")
                        }
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                importFile.launch(
                                    arrayOf(
                                        "application/json",
                                        "application/yaml",
                                        "text/*",
                                        "application/octet-stream",
                                    )
                                )
                            },
                        ) {
                            Text("Open public file")
                        }
                    }
                }
            }
            TextButton(onClick = { copyPublic(TunnelManager.diagnostics()) }) {
                Text("Copy diagnostic summary")
            }
            Text(
                "STUNMESH ${BuildConfig.VERSION_NAME} · local core build",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Recovery uses the encrypted Android backup service. The device wrapping key is never backed up. A restored profile stays off until reviewed.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    enrollment?.let { candidate ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    enrollment = null
                    psk = ""
                }
            },
            title = { Text("Verify public enrollment") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(candidate.publicConfig.name)
                    candidate.publicConfig.peers.forEach { p ->
                        Text("Server key: ${p.publicKey}", fontFamily = FontFamily.Monospace)
                        Text("Routes: ${p.allowedIps.joinToString()}")
                    }
                    Text("Phone address: ${candidate.publicConfig.iface.addresses.joinToString()}")
                    Text(
                        "Saving creates a fresh phone identity. Copy its public reply to the owner and add that peer on the server before connecting."
                    )
                    if (candidate.requiresPsk) {
                        Text(
                            "This proposal requires a separately supplied WireGuard PSK. Use a trusted keyboard; it is not part of the QR."
                        )
                        SecretPskField { psk = it }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && (!candidate.requiresPsk || psk.length == 44),
                    onClick = {
                        val secret = psk
                        psk = ""
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    repository.enroll(candidate.publicConfig, secret)
                                }
                                enrollment = null
                                notice =
                                    "Identity saved. Copy the public reply and authorize it on the server; the VPN remains off."
                            } catch (_: Throwable) {
                                notice =
                                    "Enrollment failed. Check the public profile, PSK requirement and hardware key storage."
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text("Confirm & create identity")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        enrollment = null
                        psk = ""
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
    connectReview?.let { t ->
        AlertDialog(
            onDismissRequest = { connectReview = null },
            title = { Text("Confirm server authorization") },
            text = {
                Text(
                    "Server keys:\n${t.serverKeys.joinToString("\n")}\n\nRoutes:\n${t.routes.joinToString("\n")}\n\nConfirm this phone key is authorized on the server. After a backup restore, keep the original device off. If it was lost or untrusted, use a fresh enrollment and retire the old peer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        connectReview = null
                        connect(t)
                    }
                ) {
                    Text("Authorized · connect")
                }
            },
            dismissButton = { TextButton(onClick = { connectReview = null }) { Text("Cancel") } },
        )
    }
    deletion?.let { t ->
        AlertDialog(
            onDismissRequest = { deletion = null },
            title = { Text("Remove this identity?") },
            text = {
                Text(
                    "The local private key will be removed. Retire the peer on the server separately. Without an encrypted OS backup, recovery requires a new enrollment."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletion = null
                        runWork { repository.remove(t.id) }
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton(onClick = { deletion = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SecretPskField(onChange: (String) -> Unit) {
    AndroidView(
        factory = { context ->
            EditText(context).apply {
                hint = "Pre-shared key"
                inputType =
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions =
                    EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_ACTION_DONE
                importantForAutofill =
                    android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                isSaveEnabled = false
                filterTouchesWhenObscured = true
                filters = arrayOf(android.text.InputFilter.LengthFilter(44))
                addTextChangedListener(
                    object : android.text.TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int,
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int,
                        ) {
                            onChange(s?.toString().orEmpty())
                        }

                        override fun afterTextChanged(s: android.text.Editable?) {}
                    }
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
