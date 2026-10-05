package com.wasif.khata.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.core.content.FileProvider
import android.app.Activity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.drive.DriveFile
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.permission.AndroidSmsPermissionChecker
import com.wasif.khata.core.permission.SmsPermissionState
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.sms.IncomingMessage
import com.wasif.khata.core.sms.ParseOutcome
import com.wasif.khata.core.sms.RuleEngine
import com.wasif.khata.core.sms.ai.DraftedRule
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.AmountKeypadDialog
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.component.CollapsingHeaderHeight
import com.wasif.khata.core.ui.component.CollapsingTopBar
import com.wasif.khata.core.ui.component.collapseFraction
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.FieldPalette
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.core.ui.theme.isLightColor
import dev.chrisbanes.haze.hazeSource
import kotlin.system.exitProcess
import kotlinx.coroutines.launch

/**
 * A tick or a cross, then the explanation. Replaces the words "Set" and "Not set":
 * the state is the first thing you look for and a glyph finds it faster than a
 * sentence does. The glyph carries the meaning on its own -- DESIGN.md 1.3 -- so the
 * colour is only reinforcement.
 */
@Composable
private fun StatusSupport(isSet: Boolean, text: String) {
    val spacing = LocalSpacing.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isSet) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = if (isSet) "Set" else "Not set",
            tint = if (isSet) KhataPalette.ok else KhataPalette.absent,
            modifier = Modifier.size(16.dp),
        )
        Text(text, Modifier.padding(start = spacing.xs))
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenUnmatched: () -> Unit,
    onOpenReconcile: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    // Hoisted because the backup slot needs it too, and collecting twice would be two
    // subscriptions to the same flow.
    val prefs = viewModel.state.collectAsStateWithLifecycle().value
    SettingsContent(
        prefs = prefs,
        ingestion = viewModel.ingestion.collectAsStateWithLifecycle().value,
        onBack = onBack,
        onPermissionRequested = viewModel::onPermissionRequested,
        onBackfill = viewModel::onBackfill,
        onSetCash = viewModel::onSetCash,
        onStartOver = viewModel::onStartOver,
        onReparse = viewModel::onReparse,
        onOpenUnmatched = onOpenUnmatched,
        onOpenReconcile = onOpenReconcile,
        onOpenCategories = onOpenCategories,
        onGeminiKeyChanged = viewModel::onGeminiKeyChanged,
        onTmdbKeyChanged = viewModel::onTmdbKeyChanged,
        backupSection = { BackupSection(prefs = prefs, viewModel = viewModel) },
        onHomeViewSelected = viewModel::onHomeViewSelected,
        onFieldSelected = viewModel::onFieldSelected,
        onGroundSelected = viewModel::onGroundSelected,
        onAccentSelected = viewModel::onAccentSelected,
        onIntensitySelected = viewModel::onIntensitySelected,
        onResetTheme = viewModel::onResetTheme,
        onLoadInboxThreads = viewModel::inboxThreads,
        onToggleWatchedSender = viewModel::onToggleWatchedSender,
        onTestSmsWithGemini = viewModel::testSmsWithGemini,
        onSavePlaygroundRule = viewModel::savePlaygroundRule,
        onSaveAccount = viewModel::onSaveAccount,
        onSmsStartYearMonthChanged = viewModel::onSmsStartYearMonthChanged,
    )
}

@Composable
fun SettingsContent(
    prefs: KhataPreferences,
    ingestion: IngestionState,
    onBack: () -> Unit,
    onPermissionRequested: () -> Unit,
    onBackfill: () -> Unit,
    onSetCash: (Money) -> Unit,
    onStartOver: (Money) -> Unit,
    onReparse: () -> Unit,
    onOpenUnmatched: () -> Unit,
    onOpenReconcile: () -> Unit,
    onOpenCategories: () -> Unit,
    onGeminiKeyChanged: (String?) -> Unit,
    onTmdbKeyChanged: (String?) -> Unit,
    /**
     * A slot rather than the view model itself. Backup needs ten methods off
     * SettingsViewModel, and taking the whole thing here meant a screen test could not
     * construct this composable at all -- eleven dependencies to assert that "HOME
     * VIEW" is on screen is not a test anybody writes, so the test rotted instead.
     */
    backupSection: @Composable () -> Unit,
    onHomeViewSelected: (HomeView) -> Unit,
    onFieldSelected: (FieldPalette) -> Unit,
    onGroundSelected: (Color) -> Unit,
    onAccentSelected: (Color) -> Unit,
    onIntensitySelected: (FieldIntensity) -> Unit,
    onResetTheme: () -> Unit,
    onLoadInboxThreads: suspend () -> List<IncomingMessage> = { emptyList() },
    onToggleWatchedSender: (String) -> Unit = {},
    onTestSmsWithGemini: suspend (String, String) -> DraftedRule? = { _, _ -> null },
    onSavePlaygroundRule: suspend (DraftedRule, String, String) -> Boolean = { _, _, _ -> false },
    onSaveAccount: (Long?, String, String) -> Unit = { _, _, _ -> },
    onSmsStartYearMonthChanged: (String?) -> Unit = {},
) {
    val spacing = LocalSpacing.current
    val scroll = rememberScrollState()
    var testingSms by rememberSaveable { mutableStateOf(false) }

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Box(Modifier.fillMaxSize()) {
            // Registered as a Haze source and padded down by the header's height, so
            // the page passes blurred under the bar rather than stopping at it.
            Column(
                Modifier
                    .fillMaxSize()
                    .hazeSource(haze)
                    // Consumed here so the viewport shrinks and the focused field
                    // scrolls into view, instead of the window panning and taking
                    // the top bar off screen with it.
                    .imePadding()
                    .verticalScroll(scroll)
                    .padding(top = CollapsingHeaderHeight, bottom = spacing.xxl)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {

                MessagesSection(
                    state = ingestion,
                    watchedSenders = prefs.watchedSenders,
                    smsStartYearMonth = prefs.smsStartYearMonth,
                    onPermissionRequested = onPermissionRequested,
                    onBackfill = onBackfill,
                    onReparse = onReparse,
                    onOpenUnmatched = onOpenUnmatched,
                    onOpenReconcile = onOpenReconcile,
                    onLoadInboxThreads = onLoadInboxThreads,
                    onToggleWatchedSender = onToggleWatchedSender,
                    onSaveAccount = onSaveAccount,
                    onSmsStartYearMonthChanged = onSmsStartYearMonthChanged,
                )

                SectionLabel("Categories")
                ActionRow(
                    title = "Manage categories",
                    subtitle = "Add, rename or retire. Past transactions keep whatever " +
                        "they were filed under.",
                    onClick = onOpenCategories,
                )

                backupSection()

                SectionLabel("AI fallback")
                GeminiKeyField(current = prefs.geminiKey, onChange = onGeminiKeyChanged)
                ActionRow(
                    title = "Test SMS with Gemini",
                    subtitle = "Paste any SMS to preview extracted fields and save a rule.",
                    enabled = prefs.geminiKey != null,
                    onClick = { testingSms = true },
                )

                TmdbKeyField(current = prefs.tmdbKey, onChange = onTmdbKeyChanged)

                if (testingSms) {
                    SmsPlaygroundDialog(
                        onDismiss = { testingSms = false },
                        onTest = onTestSmsWithGemini,
                        onSave = onSavePlaygroundRule,
                    )
                }


                SectionLabel("Home view")
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    HomeView.entries.forEach { view ->
                        Pill(
                            text = view.name,
                            selected = prefs.homeView == view,
                            modifier = Modifier.weight(1f),
                        ) { onHomeViewSelected(view) }
                    }
                }
                // I6: KhataNavHost freezes its start destination for the process
                // lifetime -- back from the root has to keep exiting the app, which
                // stops being true the instant the graph could be rebuilt under a
                // live back stack. So this control cannot take effect immediately;
                // saying so here is the other half of that fix; freezing alone
                // would make the control look broken instead.
                Text(
                    text = "Takes effect the next time you open Khata",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = spacing.screenHorizontal,
                        end = spacing.screenHorizontal,
                        top = spacing.xs,
                    ),
                )

                SectionLabel("Field")
                SwatchGrid(
                    swatches = KhataPalette.fields.map { it.name to it.keyStop },
                    selectedIndex = KhataPalette.fields.indexOf(prefs.themeSpec.field),
                    onSelect = { onFieldSelected(KhataPalette.fields[it]) },
                )

                SectionLabel("Ground")
                SwatchGrid(
                    swatches = KhataPalette.grounds.map { it.name to it.color },
                    selectedIndex = KhataPalette.grounds.indexOfFirst { it.color == prefs.themeSpec.ground },
                    onSelect = { onGroundSelected(KhataPalette.grounds[it].color) },
                )

                SectionLabel("Accent")
                SwatchGrid(
                    swatches = KhataPalette.accents.map { it.name to it.color },
                    selectedIndex = KhataPalette.accents.indexOfFirst { it.color == prefs.themeSpec.accent },
                    onSelect = { onAccentSelected(KhataPalette.accents[it].color) },
                )

                SectionLabel("Field intensity")
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    FieldIntensity.entries.forEach { level ->
                        Pill(
                            text = level.name,
                            selected = prefs.themeSpec.intensity == level,
                            modifier = Modifier.weight(1f),
                        ) { onIntensitySelected(level) }
                    }
                }

                Box(
                    Modifier
                        .padding(spacing.screenHorizontal)
                        .fillMaxWidth()
                        .height(spacing.minTouchTarget)
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable { onResetTheme() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Reset to default",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                WalletSection(
                    cashBalance = ingestion.cashBalance,
                    onSetCash = onSetCash,
                    onStartOver = onStartOver,
                )
            }

            CollapsingTopBar(
                heading = "Settings",
                subline = "Messages · home · theme · budget",
                collapse = scroll.collapseFraction(),
                hazeState = haze,
                onBack = onBack,
            )
        }
    }
}

/**
 * Last on the page on purpose: both rows overwrite what the ledger worked out, and
 * one of them throws it away. Nothing should reach them on the way to somewhere else.
 */
@Composable
private fun WalletSection(
    cashBalance: Money,
    onSetCash: (Money) -> Unit,
    onStartOver: (Money) -> Unit,
) {
    var settingCash by rememberSaveable { mutableStateOf(false) }
    var confirmingStartOver by rememberSaveable { mutableStateOf(false) }
    var startOverCash by rememberSaveable { mutableStateOf(false) }

    SectionLabel("Wallet")

    ActionRow(
        title = "Set cash value in wallet",
        subtitle = "Cash reads ${cashBalance.format()}. Count what is in your pocket and enter it; " +
            "the difference is recorded as an adjustment.",
        onClick = { settingCash = true },
    )

    ActionRow(
        title = "Start over wallet calculations",
        subtitle = "Throws away every transaction and works every balance out again " +
            "from nothing.",
        onClick = { confirmingStartOver = true },
    )

    if (settingCash) {
        AmountDialog(
            title = "Set cash value",
            body = "Cash currently reads ${cashBalance.format()}.",
            confirmLabel = "Set",
            initial = cashBalance,
            onDismiss = { settingCash = false },
            onConfirm = {
                settingCash = false
                onSetCash(it)
            },
        )
    }

    if (confirmingStartOver) {
        AlertDialog(
            onDismissRequest = { confirmingStartOver = false },
            title = { Text("Start over?") },
            text = {
                Text(
                    "Every transaction Khata holds is deleted and every balance is " +
                        "worked out again from nothing. Bank and bKash balances are taken " +
                        "from the most recent message each one sent. Cash has no message, " +
                        "so Khata will ask you what it holds. Your messages are kept, so " +
                        "Re-read with the current rules can rebuild the history " +
                        "afterwards. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingStartOver = false
                    startOverCash = true
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingStartOver = false }) { Text("Cancel") }
            },
        )
    }

    if (startOverCash) {
        AmountDialog(
            title = "How much cash do you have?",
            body = "Count the notes in your pocket. Every other balance is read back " +
                "out of your messages.",
            confirmLabel = "Start over",
            initial = Money.ZERO,
            onDismiss = { startOverCash = false },
            onConfirm = {
                startOverCash = false
                onStartOver(it)
            },
        )
    }
}

/**
 * Confirm stays disabled until the keys spell a sum. Both callers write a balance
 * outright, and an empty readout counting as zero would be a wipe nobody typed.
 */
@Composable
private fun AmountDialog(
    title: String,
    body: String,
    confirmLabel: String,
    initial: Money,
    onDismiss: () -> Unit,
    onConfirm: (Money) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial.format(withSymbol = false)) }
    val amount = Money.parse(text)

    AmountKeypadDialog(
        title = title,
        body = body,
        value = text,
        onValueChange = { text = it },
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        confirmEnabled = amount != null,
        onConfirm = { amount?.let(onConfirm) },
    )
}

@Composable
private fun MessagesSection(
    state: IngestionState,
    watchedSenders: Set<String> = KhataPreferences.DEFAULT_WATCHED_SENDERS,
    smsStartYearMonth: String? = null,
    onPermissionRequested: () -> Unit,
    onBackfill: () -> Unit,
    onReparse: () -> Unit,
    onOpenUnmatched: () -> Unit,
    onOpenReconcile: () -> Unit,
    onLoadInboxThreads: suspend () -> List<IncomingMessage> = { emptyList() },
    onToggleWatchedSender: (String) -> Unit = {},
    onSaveAccount: (Long?, String, String) -> Unit = { _, _, _ -> },
    onSmsStartYearMonthChanged: (String?) -> Unit = {},
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    var pickingThreads by rememberSaveable { mutableStateOf(false) }
    var managingAccounts by rememberSaveable { mutableStateOf(false) }
    var pickingStartMonth by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onPermissionRequested() }

    SectionLabel("Messages")

    // Permission is described by what it does, not by its enum name.
    ActionRow(
        title = when (state.permission) {
            SmsPermissionState.GRANTED -> "Reading your messages"
            SmsPermissionState.NOT_REQUESTED -> "Read bKash and EBL messages"
            SmsPermissionState.DENIED -> "Message access is off"
            SmsPermissionState.PERMANENTLY_DENIED -> "Message access is off"
        },
        subtitle = when (state.permission) {
            SmsPermissionState.GRANTED -> "New messages become transactions on their own"
            SmsPermissionState.NOT_REQUESTED -> "Khata works without this. You would enter each one by hand."
            SmsPermissionState.DENIED -> "Tap to ask again"
            // The system dialog stops appearing after two refusals, so "tap to
            // ask again" here would be a promise Android will not keep.
            SmsPermissionState.PERMANENTLY_DENIED -> "Android will not ask again. Turn it on in app settings."
        },
        enabled = !state.permission.enablesCapture,
        onClick = {
            if (state.permission.needsAppSettings) {
                context.startActivity(
                    Intent(
                        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            } else {
                launcher.launch(AndroidSmsPermissionChecker.PERMISSIONS)
            }
        },
    )

    ActionRow(
        title = "Watched SMS threads",
        subtitle = watchedSenders.sorted().joinToString(", ").ifEmpty { "None selected" },
        onClick = { pickingThreads = true },
    )

    if (pickingThreads) {
        WatchedThreadsDialog(
            watchedSenders = watchedSenders,
            onLoadInboxThreads = onLoadInboxThreads,
            onToggle = onToggleWatchedSender,
            onDismiss = { pickingThreads = false },
        )
    }

    ActionRow(
        title = "Bank accounts & SMS tails",
        subtitle = state.accounts.joinToString(" · ") { "${it.name} (${it.smsIdentifiers})" }
            .ifEmpty { "Map account numbers or card tails to separate accounts" },
        onClick = { managingAccounts = true },
    )

    if (managingAccounts) {
        AccountTailsDialog(
            accounts = state.accounts,
            onSave = onSaveAccount,
            onDismiss = { managingAccounts = false },
        )
    }

    val startSubtitle = remember(smsStartYearMonth) {
        val ym = smsStartYearMonth?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() }
        if (ym == null) {
            "All time"
        } else {
            val monthName = ym.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
            "From $monthName ${ym.year} onwards"
        }
    }

    ActionRow(
        title = "Read messages starting from",
        subtitle = startSubtitle,
        onClick = { pickingStartMonth = true },
    )

    if (pickingStartMonth) {
        StartMonthDialog(
            current = smsStartYearMonth,
            onSelect = {
                onSmsStartYearMonthChanged(it)
                pickingStartMonth = false
            },
            onDismiss = { pickingStartMonth = false },
        )
    }

    ActionRow(
        title = "Read my message history",
        subtitle = "Goes through every message already on this phone. Safe to run twice.",
        enabled = state.permission.enablesCapture && !state.isWorking,
        onClick = onBackfill,
    )

    state.backfill?.let { progress ->
        Column(Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal)) {
            Text(
                // Words as well as a bar: a bar alone cannot say how far along it is.
                text = "${progress.processed} of ${progress.total} messages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth().padding(top = spacing.xs),
            )
        }
    }

    ActionRow(
        title = "Re-read with the current rules",
        subtitle = "A rule written today fixes messages received months ago.",
        enabled = !state.isWorking,
        onClick = onReparse,
    )

    state.lastRun?.let { summary ->
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        )
    }

    ActionRow(
        title = "Messages Khata could not read",
        subtitle = if (state.unmatchedCount == 0) {
            "Nothing waiting"
        } else {
            "${state.unmatchedCount} waiting. Teach Khata to read them."
        },
        onClick = onOpenUnmatched,
    )

    ActionRow(
        title = "Check balances",
        subtitle = "Your balances come from the bank. See what has no message behind it.",
        onClick = onOpenReconcile,
    )
}

@Composable
private fun WatchedThreadsDialog(
    watchedSenders: Set<String>,
    onLoadInboxThreads: suspend () -> List<IncomingMessage>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var query by rememberSaveable { mutableStateOf("") }
    var inboxThreads by remember { mutableStateOf<List<IncomingMessage>>(emptyList()) }

    LaunchedEffect(Unit) {
        inboxThreads = onLoadInboxThreads()
    }

    val allThreads = remember(watchedSenders, inboxThreads, query) {
        val bySender = inboxThreads.associateBy { it.sender.uppercase() }
        val combined = buildList {
            watchedSenders.sorted().forEach { watched ->
                add(bySender[watched.uppercase()] ?: IncomingMessage(watched, "Watched thread", 0L))
            }
            inboxThreads.forEach { msg ->
                if (watchedSenders.none { it.equals(msg.sender, ignoreCase = true) }) {
                    add(msg)
                }
            }
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            combined
        } else {
            val filtered = combined.filter {
                it.sender.contains(trimmed, ignoreCase = true) ||
                    it.body.contains(trimmed, ignoreCase = true)
            }
            if (filtered.none { it.sender.equals(trimmed, ignoreCase = true) }) {
                filtered + IncomingMessage(trimmed, "Add \"$trimmed\"", 0L)
            } else {
                filtered
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watched SMS threads") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search SMS threads") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (allThreads.isEmpty()) {
                        Text(
                            text = "No SMS threads found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = spacing.sm),
                        )
                    } else {
                        allThreads.forEach { thread ->
                            val isWatched = watchedSenders.any {
                                it.equals(thread.sender, ignoreCase = true)
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(thread.sender) }
                                    .padding(vertical = spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isWatched,
                                    onCheckedChange = { onToggle(thread.sender) },
                                )
                                Column(Modifier.weight(1f).padding(start = spacing.xs)) {
                                    Text(
                                        text = thread.sender,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = thread.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun AccountTailsDialog(
    accounts: List<AccountEntity>,
    onSave: (Long?, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingNew by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var identifiers by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bank accounts & SMS tails") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = "Map each account to its SMS sender (e.g. bKash) or last 3 account/card digits (e.g. 352, 286).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                accounts.forEach { acc ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingId = acc.id
                                editingNew = false
                                name = acc.name
                                identifiers = acc.smsIdentifiers
                            }
                            .padding(vertical = spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = acc.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "SMS match: ${acc.smsIdentifiers.ifBlank { "None" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (editingId != null || editingNew) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Account name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = identifiers,
                        onValueChange = { identifiers = it },
                        label = { Text("SMS sender or 3-digit tails (comma-separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    TextButton(
                        onClick = {
                            editingId = null
                            editingNew = true
                            name = ""
                            identifiers = ""
                        },
                    ) { Text("Add account") }
                }
            }
        },
        confirmButton = {
            if (editingId != null || editingNew) {
                TextButton(
                    enabled = name.isNotBlank() && identifiers.isNotBlank(),
                    onClick = {
                        onSave(editingId, name, identifiers)
                        editingId = null
                        editingNew = false
                    },
                ) { Text("Save") }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (editingId != null || editingNew) {
                TextButton(
                    onClick = {
                        editingId = null
                        editingNew = false
                    },
                ) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun StartMonthDialog(
    current: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val now = remember { java.time.YearMonth.now() }
    val initialYm = remember(current) {
        current?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() }
    }
    var selectedYear by rememberSaveable { mutableStateOf(initialYm?.year ?: now.year) }
    var selectedMonth by rememberSaveable { mutableStateOf(initialYm?.monthValue ?: now.monthValue) }
    val monthLabels = remember {
        java.time.Month.entries.map {
            it.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
        }.toTypedArray()
    }
    val textColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read messages starting from") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = "Messages before this month are skipped when reading history, re-reading rules, or starting over.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier.weight(1f),
                        factory = { ctx ->
                            android.widget.NumberPicker(ctx).apply {
                                minValue = 1
                                maxValue = 12
                                displayedValues = monthLabels
                                wrapSelectorWheel = true
                                descendantFocusability = android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS
                                textColor = textColorArgb
                                value = selectedMonth
                                setOnValueChangedListener { _, _, newVal ->
                                    selectedMonth = newVal
                                }
                            }
                        },
                        update = { picker ->
                            picker.textColor = textColorArgb
                            if (picker.value != selectedMonth) picker.value = selectedMonth
                        },
                    )
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier.weight(1f),
                        factory = { ctx ->
                            android.widget.NumberPicker(ctx).apply {
                                minValue = 2015
                                maxValue = now.year
                                wrapSelectorWheel = false
                                descendantFocusability = android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS
                                textColor = textColorArgb
                                value = selectedYear.coerceIn(2015, now.year)
                                setOnValueChangedListener { _, _, newVal ->
                                    selectedYear = newVal
                                }
                            }
                        },
                        update = { picker ->
                            picker.textColor = textColorArgb
                            val clamped = selectedYear.coerceIn(2015, now.year)
                            if (picker.value != clamped) picker.value = clamped
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(java.time.YearMonth.of(selectedYear, selectedMonth).toString())
                },
            ) { Text("Set") }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSelect(null) }) { Text("All time") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun SmsPlaygroundDialog(
    onDismiss: () -> Unit,
    onTest: suspend (String, String) -> DraftedRule?,
    onSave: suspend (DraftedRule, String, String) -> Boolean,
) {
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()
    val engine = remember { RuleEngine() }

    var sender by rememberSaveable { mutableStateOf("bKash") }
    var sample by rememberSaveable { mutableStateOf("") }
    var asking by remember { mutableStateOf(false) }
    var drafted by remember { mutableStateOf<DraftedRule?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    var name by rememberSaveable { mutableStateOf("") }
    var senderPattern by rememberSaveable { mutableStateOf("") }
    var bodyPattern by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(RuleKind.NORMAL) }
    var direction by rememberSaveable { mutableStateOf(TransactionDirection.DEBIT) }

    val previewOutcome = remember(sender, sample, name, senderPattern, bodyPattern, kind, direction) {
        if (sample.isBlank() || bodyPattern.isBlank()) {
            null
        } else {
            val rule = ParsingRuleEntity(
                uuid = "preview",
                name = name.ifBlank { "Preview" },
                senderPattern = senderPattern.ifBlank { Regex.escape(sender.trim()) },
                bodyPattern = bodyPattern,
                direction = if (kind != RuleKind.IGNORE) direction else null,
                kind = kind,
                priority = 0,
                origin = "AI",
                isEnabled = true,
                sampleMessage = sample,
                createdAt = 0L,
                updatedAt = 0L,
            )
            engine.parse(sender.trim(), sample, listOf(rule))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test SMS with Gemini") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("Sender") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    label = { Text("Paste SMS message") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    enabled = !asking && sender.isNotBlank() && sample.isNotBlank(),
                    onClick = {
                        asking = true
                        status = null
                        scope.launch {
                            val res = onTest(sender.trim(), sample.trim())
                            asking = false
                            if (res == null) {
                                status = "Gemini could not draft a rule for this message."
                            } else {
                                drafted = res
                                name = res.name
                                senderPattern = res.senderPattern
                                bodyPattern = res.bodyPattern
                                kind = res.kind
                                res.direction?.let { direction = it }
                            }
                        }
                    },
                ) {
                    Text(if (asking) "Asking Gemini..." else "Test with Gemini")
                }

                status?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (drafted != null || bodyPattern.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Pill(
                            text = "DEBIT",
                            selected = kind != RuleKind.IGNORE && direction == TransactionDirection.DEBIT,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (kind == RuleKind.IGNORE) kind = RuleKind.NORMAL
                            direction = TransactionDirection.DEBIT
                        }
                        Pill(
                            text = "CREDIT",
                            selected = kind != RuleKind.IGNORE && direction == TransactionDirection.CREDIT,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (kind == RuleKind.IGNORE) kind = RuleKind.NORMAL
                            direction = TransactionDirection.CREDIT
                        }
                        Pill(
                            text = "IGNORE",
                            selected = kind == RuleKind.IGNORE,
                            modifier = Modifier.weight(1f),
                        ) {
                            kind = RuleKind.IGNORE
                        }
                    }

                    drafted?.let { d ->
                        val aiFields = buildList {
                            d.amount?.let { add("Amount: $it") }
                            d.merchant?.let { add("Merchant: $it") }
                            d.datetime?.let { add("Time: $it") }
                            d.balance?.let { add("Balance: $it") }
                        }
                        if (aiFields.isNotEmpty()) {
                            Text(
                                text = "AI extracted: " + aiFields.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    previewOutcome?.let { outcome ->
                        val matchSummary = when (outcome) {
                            is ParseOutcome.Parsed -> buildList {
                                add("Regex matches (${outcome.value.amount.format()})")
                                outcome.value.merchant?.let { add("merchant=$it") }
                                outcome.value.providerTxnId?.let { add("ref=$it") }
                                outcome.value.balance?.let { add("bal=${it.format()}") }
                            }.joinToString(" · ")
                            is ParseOutcome.Ignored -> "Regex matches as ignored non-transaction"
                            ParseOutcome.Unmatched -> "Regex does not match sample yet"
                        }
                        Text(
                            text = matchSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (outcome is ParseOutcome.Unmatched) {
                                KhataPalette.warn
                            } else {
                                KhataPalette.ok
                            },
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Rule name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = senderPattern,
                        onValueChange = { senderPattern = it },
                        label = { Text("Sender regex") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = bodyPattern,
                        onValueChange = { bodyPattern = it },
                        label = { Text("Body regex") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = bodyPattern.isNotBlank() && sample.isNotBlank(),
                onClick = {
                    scope.launch {
                        val toSave = DraftedRule(
                            name = name.ifBlank { sender.trim() },
                            senderPattern = senderPattern.ifBlank { Regex.escape(sender.trim()) },
                            bodyPattern = bodyPattern,
                            direction = if (kind != RuleKind.IGNORE) direction else null,
                            kind = kind,
                        )
                        if (onSave(toSave, sender.trim(), sample.trim())) {
                            onDismiss()
                        } else {
                            status = "Regex must match the sample (and capture (?<amount>...)) before saving."
                        }
                    }
                },
            ) { Text("Save rule") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                // Disabled is carried by the same two text tiers the rest of the
                // app uses, never by a third colour.
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (enabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Renders [swatches] (name to colour, kept as one pair so the label can never
 * drift from the colour it names -- see `KhataPalette.NamedSwatch`).
 *
 * Selection was border colour alone: `primary` at 2dp vs `outlineVariant` at
 * 1dp, and the unselected width barely reads as a border at all. That is
 * exactly the "colour is never the sole signal" rule applied to the one
 * screen whose subject is colour, so a checkmark badge is the second signal
 * here -- its own two colours flip based on the swatch's lightness so it stays
 * legible whether the swatch is a near-black ground or a pastel accent.
 */
@Composable
private fun SwatchGrid(
    swatches: List<Pair<String, Color>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        swatches.chunked(4).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                row.forEachIndexed { colIndex, (name, colour) ->
                    val index = rowIndex * 4 + colIndex
                    val selected = index == selectedIndex
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(index) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = if (selected) "$name, selected" else name
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(colour)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = MaterialTheme.shapes.small,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                // onSurface/heroStops is the pair ContrastTest
                                // already proves clears 4.5:1 against each
                                // other; isLightColor picks which one of the
                                // two goes on the badge vs the checkmark so
                                // the badge itself stays visible on the swatch.
                                val badgeBg = if (isLightColor(colour)) {
                                    KhataPalette.heroStops.last()
                                } else {
                                    KhataPalette.onSurface
                                }
                                val badgeIcon = if (isLightColor(colour)) {
                                    KhataPalette.onSurface
                                } else {
                                    KhataPalette.heroStops.last()
                                }
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(badgeBg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = badgeIcon,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = spacing.xs).fillMaxWidth(),
                        )
                    }
                }
                // Keep a short last row's cells the same width as a full row's.
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The stored key is never rendered back. Nothing needs to read it on screen, and a key
 * on a screen is a key in a screenshot -- so the field starts empty and the supporting
 * text says only whether one is set.
 */
@Composable
private fun GeminiKeyField(current: String?, onChange: (String?) -> Unit) {
    val spacing = LocalSpacing.current
    var text by rememberSaveable { mutableStateOf("") }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            onChange(input.ifBlank { null })
        },
        label = { Text("Gemini API key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        supportingText = {
            StatusSupport(
                isSet = current != null,
                text = if (current == null) {
                    "A message no rule matches stays in the review list."
                } else {
                    "A message no rule matches is sent to Gemini to draft a rule."
                },
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal),
    )
}

/**
 * The same shape as the Gemini field beside it. Without a key the watchlist's search
 * box simply returns nothing and the manual fields take over, so this is an
 * enhancement to switch on rather than a setting to get right.
 */
@Composable
private fun TmdbKeyField(current: String?, onChange: (String?) -> Unit) {
    val spacing = LocalSpacing.current
    var text by rememberSaveable { mutableStateOf("") }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            onChange(input.ifBlank { null })
        },
        label = { Text("TMDB API key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        supportingText = {
            StatusSupport(
                isSet = current != null,
                text = if (current == null) {
                    "Titles are typed in by hand."
                } else {
                    "Searching TMDB fills in the year, poster and public rating."
                },
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal),
    )
}

@Composable
private fun BackupSection(prefs: KhataPreferences, viewModel: SettingsViewModel) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    var passphrase by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf(false) }
    var restorePassphrase by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var driveFiles by remember { mutableStateOf<List<DriveFile>?>(null) }

    val connect = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConnectResult(result.data) }

    // Null while looking, so the chooser can say so rather than claim Drive is empty.
    LaunchedEffect(choosing, prefs.driveConnected) {
        driveFiles = null
        if (choosing && prefs.driveConnected) driveFiles = viewModel.driveBackupList()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pending = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    SectionLabel("Backup")

    OutlinedTextField(
        value = passphrase,
        onValueChange = {
            passphrase = it
            viewModel.onBackupPassphraseChanged(it.ifBlank { null })
        },
        label = { Text("Backup passphrase") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        supportingText = {
            StatusSupport(
                isSet = prefs.backupKey != null,
                // One line, and still the thing that matters: there is no recovery
                // path and there cannot be one, which is what makes the file safe to
                // put anywhere.
                text = if (prefs.backupKey == null) {
                    "No backups are being taken."
                } else {
                    "Keep it somewhere safe, don't lose it."
                },
            )
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
    )

    ActionRow(
        title = "Back up now",
        subtitle = "Writes an encrypted copy. Seven are kept.",
        enabled = prefs.backupKey != null,
        onClick = viewModel::onBackUpNow,
    )

    ActionRow(
        title = "Share the latest backup",
        subtitle = "Send it to Drive, email, or a cable. The file is encrypted.",
        enabled = prefs.backupKey != null,
        onClick = {
            val file = viewModel.latestBackup() ?: return@ActionRow
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.backups",
                file,
            )
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Share backup",
                ),
            )
        },
    )

    ActionRow(
        title = if (!prefs.driveConnected) "Connect Google Drive" else "Disconnect Google Drive",
        // Not decoration. A backup system that quietly stops is worse than one that
        // never existed, because it is trusted -- this line is the only thing that
        // says the offsite copy is real.
        subtitle = when {
            !prefs.driveConnected -> "Not connected. Backups stay on this phone."
            prefs.driveNeedsReconnect -> "Uploads have stopped. Tap to reconnect."
            prefs.driveLastUploadAt != null ->
                "Connected - last uploaded ${stamp(prefs.driveLastUploadAt!!)}"
            else -> "Connected - nothing uploaded yet"
        },
        onClick = {
            if (prefs.driveConnected && !prefs.driveNeedsReconnect) {
                viewModel.onDisconnectDrive()
            } else {
                (context as? Activity)?.let { activity ->
                    viewModel.onConnectDrive(activity) { sender ->
                        connect.launch(IntentSenderRequest.Builder(sender).build())
                    }
                }
            }
        },
    )

    ActionRow(
        title = "Restore a backup",
        subtitle = "Replaces everything in Khata. Pick one of this phone's backups, or a file.",
        onClick = { choosing = true },
    )

    if (choosing) {
        val local = viewModel.localBackups()
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Restore which backup?") },
            text = {
                Column {
                    SectionLabel("On this phone")
                    if (local.isEmpty()) {
                        Text(
                            text = "This phone has no backups yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        local.forEach { file ->
                            Text(
                                text = backupLabel(file),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        pending = file.readBytes()
                                        choosing = false
                                    }
                                    .padding(vertical = spacing.sm),
                            )
                        }
                    }

                    if (prefs.driveConnected) {
                        SectionLabel("In Drive")
                        val found = driveFiles
                        when {
                            found == null -> Text(
                                text = "Looking...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            found.isEmpty() -> Text(
                                text = "Nothing in Drive, or Drive is unreachable.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> found.forEach { file ->
                                Text(
                                    text = driveLabel(file.name),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            choosing = false
                                            // Downloads, then falls into the same
                                            // passphrase dialog as a local file.
                                            scope.launch {
                                                pending = viewModel.downloadFromDrive(file.id)
                                            }
                                        }
                                        .padding(vertical = spacing.sm),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        choosing = false
                        picker.launch(arrayOf("*/*"))
                    },
                ) { Text("Choose a file") }
            },
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
        )
    }

    // Cleared as the dialog opens, not only after a successful restore. It is
    // rememberSaveable, so a second attempt used to append to the first -- typing
    // the right passphrase into a field that already held one, and being told it
    // was wrong.
    LaunchedEffect(pending) { if (pending != null) restorePassphrase = "" }

    pending?.let { bytes ->
        AlertDialog(
            onDismissRequest = { pending = null; restorePassphrase = "" },
            title = { Text("Restore this backup?") },
            text = {
                Column {
                    Text("Everything currently in Khata is replaced. Enter the passphrase the backup was made with.")
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onRestore(bytes, restorePassphrase) { result ->
                            message = result
                            pending = null
                            restorePassphrase = ""
                        }
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null; restorePassphrase = "" }) { Text("Cancel") }
            },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Restore") },
            text = { Text(text) },
            confirmButton = {
                TextButton(
                    onClick = {
                        message = null
                        // Room is holding a handle to a file that was replaced
                        // underneath it. Only a fresh process is safe.
                        //
                        // finishAffinity alone is not that: it ends the activities
                        // and leaves the process cached, so the next launch reuses
                        // it -- same pid, same closed-then-reopened Room. Verified
                        // on the device. exitProcess is what actually makes the
                        // next launch read the restored file from scratch.
                        if (text.startsWith("Restored")) {
                            (context as? Activity)?.finishAffinity()
                            exitProcess(0)
                        }
                    },
                ) { Text("OK") }
            },
        )
    }
}

/** "3 Sep, 02:00" -- the same shape as backupLabel, without the year. */
private fun stamp(millis: Long): String = java.time.Instant.ofEpochMilli(millis)
    .atZone(com.wasif.khata.core.time.DHAKA)
    .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale.ENGLISH))

/** The same label as backupLabel, from a Drive name rather than a File. */
private fun driveLabel(name: String): String {
    val millis = name.removePrefix("khata-").removeSuffix(".kbk").toLongOrNull() ?: return name
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(com.wasif.khata.core.time.DHAKA)
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.ENGLISH))
}

/** "3 Sep 2026, 19:56" from the millis in the filename. */
private fun backupLabel(file: java.io.File): String {
    val millis = file.name.removePrefix("khata-").removeSuffix(".kbk").toLongOrNull()
        ?: return file.name
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(com.wasif.khata.core.time.DHAKA)
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.ENGLISH))
}
