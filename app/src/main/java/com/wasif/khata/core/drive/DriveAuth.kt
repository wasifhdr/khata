package com.wasif.khata.core.drive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.wasif.khata.core.prefs.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Per-file access to files this app created. Deliberately not `drive` or
 * `drive.appdata`: this one is non-sensitive, needs no Google verification, and
 * cannot see anything in the user's Drive that Khata did not put there.
 */
const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

sealed interface TokenResult {
    /** Valid for about an hour, and never persisted. */
    data class Token(val value: String) : TokenResult
    data object NotConnected : TokenResult
    data object NeedsReconnect : TokenResult
}

/**
 * The only file in Khata that touches Play Services.
 *
 * Two paths into one AuthorizationClient. Consent needs an Activity and happens in
 * Settings; the nightly upload has none, which is why the Context overload of
 * getAuthorizationClient matters -- without it an unattended upload is impossible.
 */
@Singleton
class DriveAuth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
) {

    /**
     * drive.file, and nothing else.
     *
     * No account is pinned and no identity scope is asked for. Both were tried:
     * AuthorizationResult.toGoogleSignInAccount() returns a null email even when the
     * email scope is granted, so the address is simply not available on this path.
     * It turned out not to be needed -- authorize() with no account pinned returns
     * hasResolution=false once a grant exists, which is the silent success the 02:00
     * run depends on. Play Services remembers which account granted; the app does
     * not have to, and now stores no identifier at all.
     */
    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .build()

    /**
     * A token for the stored account, without UI. Called from the worker.
     *
     * No picker appears at 02:00: once a grant exists, authorize() resolves against
     * it silently.
     */
    suspend fun token(): TokenResult = withContext(Dispatchers.IO) {
        if (!preferences.preferences.first().driveConnected) {
            return@withContext TokenResult.NotConnected
        }

        val result = runCatching {
            Tasks.await(Identity.getAuthorizationClient(context).authorize(request()))
        }.getOrElse { return@withContext TokenResult.NeedsReconnect }

        // hasResolution means consent is needed again, and a worker cannot show it.
        val token = result.accessToken
        if (result.hasResolution() || token.isNullOrBlank()) {
            TokenResult.NeedsReconnect
        } else {
            TokenResult.Token(token)
        }
    }

    /**
     * Null when access was already granted -- in which case the account has just been
     * stored and there is nothing to show. Otherwise the sender the caller must
     * launch for consent.
     */
    suspend fun beginConnect(activity: Activity): IntentSender? = withContext(Dispatchers.IO) {
        val result = runCatching {
            Tasks.await(Identity.getAuthorizationClient(activity).authorize(request()))
        }.getOrElse { return@withContext null }

        if (result.hasResolution()) {
            result.pendingIntent?.intentSender
        } else {
            remember(result)
            null
        }
    }

    /** Stores the account from a completed consent. False if it did not complete. */
    suspend fun completeConnect(data: Intent?): Boolean = withContext(Dispatchers.IO) {
        val result = runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        }.getOrElse { return@withContext false }

        remember(result)
    }

    /** Connected means the scope came back granted -- the only fact that matters. */
    private suspend fun remember(result: AuthorizationResult): Boolean {
        val granted = result.grantedScopes.contains(DRIVE_FILE_SCOPE)
        if (granted) preferences.setDriveConnected(true)
        return granted
    }
}
