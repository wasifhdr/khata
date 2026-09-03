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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import android.app.Activity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.wasif.khata.core.permission.AndroidSmsPermissionChecker
import com.wasif.khata.core.permission.SmsPermissionState
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.Pill
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

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenUnmatched: () -> Unit,
    onOpenReconcile: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SettingsContent(
        prefs = viewModel.state.collectAsStateWithLifecycle().value,
        ingestion = viewModel.ingestion.collectAsStateWithLifecycle().value,
        onBack = onBack,
        onPermissionRequested = viewModel::onPermissionRequested,
        onBackfill = viewModel::onBackfill,
        onResetCash = viewModel::onResetCash,
        onReparse = viewModel::onReparse,
        onOpenUnmatched = onOpenUnmatched,
        onOpenReconcile = onOpenReconcile,
        onOpenCategories = onOpenCategories,
        onGeminiKeyChanged = viewModel::onGeminiKeyChanged,
        viewModel = viewModel,
        onHomeViewSelected = viewModel::onHomeViewSelected,
        onFieldSelected = viewModel::onFieldSelected,
        onGroundSelected = viewModel::onGroundSelected,
        onAccentSelected = viewModel::onAccentSelected,
        onIntensitySelected = viewModel::onIntensitySelected,
        onResetTheme = viewModel::onResetTheme,
    )
}

@Composable
fun SettingsContent(
    prefs: KhataPreferences,
    ingestion: IngestionState,
    onBack: () -> Unit,
    onPermissionRequested: () -> Unit,
    onBackfill: () -> Unit,
    onResetCash: () -> Unit,
    onReparse: () -> Unit,
    onOpenUnmatched: () -> Unit,
    onOpenReconcile: () -> Unit,
    onOpenCategories: () -> Unit,
    onGeminiKeyChanged: (String?) -> Unit,
    viewModel: SettingsViewModel,
    onHomeViewSelected: (HomeView) -> Unit,
    onFieldSelected: (FieldPalette) -> Unit,
    onGroundSelected: (Color) -> Unit,
    onAccentSelected: (Color) -> Unit,
    onIntensitySelected: (FieldIntensity) -> Unit,
    onResetTheme: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val scroll = rememberScrollState()

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

                SectionLabel("AI fallback")
                GeminiKeyField(current = prefs.geminiKey, onChange = onGeminiKeyChanged)

                BackupSection(prefs = prefs, viewModel = viewModel)

                SectionLabel("Categories")
                ActionRow(
                    title = "Manage categories",
                    subtitle = "Add, rename or retire. Past transactions keep whatever " +
                        "they were filed under.",
                    onClick = onOpenCategories,
                )

                MessagesSection(
                    state = ingestion,
                    onPermissionRequested = onPermissionRequested,
                    onBackfill = onBackfill,
                    onResetCash = onResetCash,
                    onReparse = onReparse,
                    onOpenUnmatched = onOpenUnmatched,
                    onOpenReconcile = onOpenReconcile,
                )

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

@Composable
private fun MessagesSection(
    state: IngestionState,
    onPermissionRequested: () -> Unit,
    onBackfill: () -> Unit,
    onResetCash: () -> Unit,
    onReparse: () -> Unit,
    onOpenUnmatched: () -> Unit,
    onOpenReconcile: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
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

    // Withdrawals top the cash account up; nothing takes money out of it until
    // cash spending is entered. Six years of that leaves a figure that is honest
    // about what left the bank and wrong about what is in your pocket.
    if (state.cashBalance.minor != 0L) {
        ActionRow(
            title = "Start cash again from zero",
            subtitle = "Cash holds ${state.cashBalance.format()} that was withdrawn and never spent here. " +
                "Writes it off as of today.",
            onClick = onResetCash,
        )
    }

    ActionRow(
        title = "Check balances",
        subtitle = "Your balances come from the bank. See what has no message behind it.",
        onClick = onOpenReconcile,
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
            Text(
                if (current == null) {
                    "Not set. A message no rule matches stays in the review list."
                } else {
                    "Set. A message no rule matches is sent to Gemini to draft a rule."
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
    val scope = rememberCoroutineScope()
    var passphrase by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var restorePassphrase by rememberSaveable { mutableStateOf("") }

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
            Text(
                if (prefs.backupKey == null) {
                    "Not set. No backups are being taken."
                } else {
                    // Said here, in these words, because there is no recovery path and
                    // there cannot be one -- that is what makes the file safe to put
                    // anywhere.
                    "Set. If you lose this passphrase, every backup becomes unreadable. " +
                        "There is no way to recover them."
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
        title = "Restore from a file",
        subtitle = "Replaces everything in Khata with the contents of a backup.",
        onClick = { picker.launch(arrayOf("*/*")) },
    )

    pending?.let { bytes ->
        AlertDialog(
            onDismissRequest = { pending = null },
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
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
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
                        if (text.startsWith("Restored")) {
                            scope.launch { (context as? Activity)?.finishAffinity() }
                        }
                    },
                ) { Text("OK") }
            },
        )
    }
}
