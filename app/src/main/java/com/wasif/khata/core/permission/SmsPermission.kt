package com.wasif.khata.core.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wasif.khata.core.prefs.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SmsPermissionState {
    NOT_REQUESTED,
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
    ;

    val enablesCapture: Boolean get() = this == GRANTED

    /** Android stops showing the dialog after two refusals; only then is settings the route. */
    val needsAppSettings: Boolean get() = this == PERMANENTLY_DENIED
}

interface SmsPermissionChecker {
    fun isGranted(): Boolean

    /**
     * False before the first request *and* after a permanent denial, which is why it
     * cannot distinguish those two states on its own — the persisted "has requested"
     * flag is what separates them.
     */
    fun shouldShowRationale(): Boolean
}

@Singleton
class AndroidSmsPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsPermissionChecker {

    override fun isGranted(): Boolean =
        PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * The Activity the rationale check needs. An application context is never an
     * Activity, so asking one produced a permanent `false` here -- which made
     * DENIED unreachable and sent the user to app settings after a single
     * refusal. MainActivity hands itself over instead, and takes it back on
     * destroy so a dead Activity is not held by this singleton.
     */
    var activity: Activity? = null

    override fun shouldShowRationale(): Boolean {
        val host = activity ?: return false
        return PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(host, it) }
    }

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
    }
}

@Singleton
class SmsPermissionRepository @Inject constructor(
    private val checker: SmsPermissionChecker,
    private val prefs: PreferencesRepository,
) {
    fun observe(): Flow<SmsPermissionState> = prefs.preferences.map { stored ->
        val requested = stored.hasRequestedSmsPermission
        when {
            checker.isGranted() -> SmsPermissionState.GRANTED
            !requested -> SmsPermissionState.NOT_REQUESTED
            checker.shouldShowRationale() -> SmsPermissionState.DENIED
            else -> SmsPermissionState.PERMANENTLY_DENIED
        }
    }

    suspend fun onRequested() = prefs.setSmsPermissionRequested()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SmsPermissionModule {
    @Binds
    abstract fun bindChecker(impl: AndroidSmsPermissionChecker): SmsPermissionChecker
}
