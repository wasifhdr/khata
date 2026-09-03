package com.wasif.khata.core.drive

import android.accounts.Account
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

    private fun request(account: Account?) = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .apply { account?.let(::setAccount) }
        .build()

    /**
     * A token for the stored account, without UI. Called from the worker.
     *
     * setAccount is what stops an account picker appearing at 02:00, where nothing
     * could answer it.
     */
    suspend fun token(): TokenResult = withContext(Dispatchers.IO) {
        val email = preferences.preferences.first().driveAccount
            ?: return@withContext TokenResult.NotConnected

        val result = runCatching {
            Tasks.await(
                Identity.getAuthorizationClient(context)
                    .authorize(request(Account(email, "com.google"))),
            )
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
            Tasks.await(Identity.getAuthorizationClient(activity).authorize(request(null)))
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

    private suspend fun remember(result: AuthorizationResult): Boolean {
        val email = result.toGoogleSignInAccount()?.email ?: return false
        preferences.setDriveAccount(email)
        return true
    }
}
