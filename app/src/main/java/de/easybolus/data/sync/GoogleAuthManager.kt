package de.easybolus.data.sync

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import de.easybolus.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GoogleAccountInfo(
    val email: String,
    val displayName: String?,
    val photoUrl: String?
)

sealed class DriveAuthTokenResult {
    data class Success(val accessToken: String) : DriveAuthTokenResult()
    data class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthTokenResult()
    data class Error(val message: String) : DriveAuthTokenResult()
}

object GoogleAuthManager {

    private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    suspend fun signIn(activity: Activity): Result<GoogleAccountInfo> = runCatching {
        val webClientId = activity.getString(R.string.google_web_client_id).trim()
        require(webClientId.isNotEmpty()) {
            activity.getString(R.string.drive_missing_web_client_id)
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(activity)
        val result = credentialManager.getCredential(
            request = request,
            context = activity
        )
        val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
        GoogleAccountInfo(
            email = googleCred.id,
            displayName = googleCred.displayName,
            photoUrl = googleCred.profilePictureUri?.toString()
        )
    }

    suspend fun requestDriveAccessToken(context: Context): DriveAuthTokenResult {
        return runCatching {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            val authResult = Identity.getAuthorizationClient(context)
                .authorize(authRequest)
                .awaitTask()

            val pending = authResult.pendingIntent
            val token = authResult.accessToken
            when {
                !token.isNullOrBlank() -> DriveAuthTokenResult.Success(token)
                authResult.hasResolution() && pending != null -> DriveAuthTokenResult.NeedsResolution(pending)
                else -> DriveAuthTokenResult.Error("No Drive access token granted")
            }
        }.getOrElse { err ->
            DriveAuthTokenResult.Error(err.localizedMessage ?: "Drive authorization failed")
        }
    }

    fun extractTokenFromIntent(context: Context, data: Intent?): String? {
        if (data == null) return null
        return runCatching {
            Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        }.getOrNull()
    }

    suspend fun signOut(context: Context) {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res ->
            if (cont.isActive) cont.resume(res)
        }
        addOnFailureListener { exc ->
            if (cont.isActive) cont.resumeWithException(exc)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
}
