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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GoogleAccountInfo(
    val email: String,
    val displayName: String?,
    val photoUrl: String?
)

sealed class SignInOutcome {
    data class Success(val account: GoogleAccountInfo) : SignInOutcome()
    data class NeedsResolution(val pendingIntent: PendingIntent) : SignInOutcome()
    data class Error(val message: String) : SignInOutcome()
}

sealed class DriveAuthTokenResult {
    data class Success(val accessToken: String) : DriveAuthTokenResult()
    data class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthTokenResult()
    data class Error(val message: String) : DriveAuthTokenResult()
}

object GoogleAuthManager {

    private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email"
    private const val PROFILE_SCOPE = "https://www.googleapis.com/auth/userinfo.profile"
    private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"

    suspend fun signIn(activity: Activity): SignInOutcome {
        val webClientId = activity.getString(R.string.google_web_client_id).trim()
        if (webClientId.isNotEmpty()) {
            val credResult = runCatching {
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
            credResult.onSuccess { return SignInOutcome.Success(it) }
            // Fall through to AuthorizationClient if Credential Manager fails or is unavailable
        }

        // Fallback / Web-Client-ID-freier Pfad über Play Services AuthorizationClient (nutzt nur Android-OAuth-Client)
        return runCatching {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(
                    listOf(
                        Scope(DRIVE_FILE_SCOPE),
                        Scope(EMAIL_SCOPE),
                        Scope(PROFILE_SCOPE)
                    )
                )
                .build()
            val authResult = Identity.getAuthorizationClient(activity)
                .authorize(authRequest)
                .awaitTask()

            val pending = authResult.pendingIntent
            val token = authResult.accessToken
            when {
                !token.isNullOrBlank() -> {
                    val info = fetchUserInfo(token).getOrElse {
                        GoogleAccountInfo(email = "Google-Konto", displayName = null, photoUrl = null)
                    }
                    SignInOutcome.Success(info)
                }
                authResult.hasResolution() && pending != null -> SignInOutcome.NeedsResolution(pending)
                else -> SignInOutcome.Error("Google-Anmeldung fehlgeschlagen")
            }
        }.getOrElse { err ->
            SignInOutcome.Error(err.localizedMessage ?: "Google-Anmeldung fehlgeschlagen")
        }
    }

    suspend fun fetchUserInfo(accessToken: String): Result<GoogleAccountInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(USERINFO_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                }.orEmpty()
                if (code !in 200..299) {
                    error("UserInfo HTTP $code: $body")
                }
                val obj = JSONObject(body)
                val email = obj.optString("email").takeIf { it.isNotBlank() } ?: "Google-Konto"
                val name = obj.optString("name").takeIf { it.isNotBlank() }
                val photo = obj.optString("picture").takeIf { it.isNotBlank() }
                GoogleAccountInfo(email = email, displayName = name, photoUrl = photo)
            } finally {
                conn.disconnect()
            }
        }
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
