package com.wasif.khata.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.wasif.khata.R
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import com.wasif.khata.core.ui.theme.composeScheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun requestCodeFor(direction: TransactionDirection): Int = direction.ordinal

fun quickEntryIntent(context: Context, direction: TransactionDirection): Intent =
    Intent(context, QuickEntryActivity::class.java)
        .putExtra(EXTRA_DIRECTION, direction.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@AndroidEntryPoint
class CashWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var preferences: PreferencesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // onUpdate runs on the main thread with a short window, and the theme lives
        // in DataStore. goAsync() keeps the broadcast alive across the read instead
        // of blocking the thread or painting the default palette.
        val pending = goAsync()
        scope.launch {
            try {
                val views = buildViews(context, preferences.preferences.first().themeSpec)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /**
         * Pushed by the app when the tuner changes. RemoteViews does not observe
         * DataStore the way Glance would, and the theme can only be changed from
         * Settings, so the app process is always alive when it happens.
         */
        fun refresh(context: Context, spec: ThemeSpec) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CashWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context, spec)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context, spec: ThemeSpec): RemoteViews {
            val scheme = composeScheme(spec)
            return RemoteViews(context.packageName, R.layout.widget_cash).apply {
                // Every colour arrives here. The layout carries none, because a
                // literal would be correct in one of the tuner's 512 combinations.
                setColorStateList(
                    R.id.widget_root,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(scheme.surface.toArgb()),
                )
                setTextColor(R.id.spent, KhataPalette.moneyOut.toArgb())
                setTextColor(R.id.received, KhataPalette.moneyIn.toArgb())
                setInt(R.id.separator, "setBackgroundColor", scheme.outlineVariant.toArgb())

                setOnClickPendingIntent(R.id.spent, activityIntent(context, TransactionDirection.DEBIT))
                setOnClickPendingIntent(R.id.received, activityIntent(context, TransactionDirection.CREDIT))
            }
        }

        private fun activityIntent(context: Context, direction: TransactionDirection) =
            PendingIntent.getActivity(
                context,
                requestCodeFor(direction),
                quickEntryIntent(context, direction),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
