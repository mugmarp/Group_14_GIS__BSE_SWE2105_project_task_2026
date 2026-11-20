package com.geotrace.ui.screens.devices

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import com.geotrace.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun performGoogleSignIn(
    context: Context,
    scope: CoroutineScope,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val activity = context.findActivity()
    if (activity == null) {
        onError("Unable to resolve host Activity for Google Sign-In.")
        return
    }
    val credentialManager = CredentialManager.create(activity)
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onError("Google Client ID not found. Ensure google-services.json is configured.")
        return
    }

    val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val primaryRequest = GetCredentialRequest.Builder()
        .addCredentialOption(signInWithGoogleOption)
        .build()

    scope.launch {
        try {
            val result = try {
                credentialManager.getCredential(activity, primaryRequest)
            } catch (interrupted: GetCredentialInterruptedException) {
                Log.w("DevicesAuth", "Primary CredentialManager interrupted, retrying once: ${interrupted.errorMessage}")
                credentialManager.getCredential(activity, primaryRequest)
            } catch (noCred: NoCredentialException) {
                Log.w("DevicesAuth", "GetSignInWithGoogleOption had no credential, falling back to GetGoogleIdOption: ${noCred.errorMessage}")
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                credentialManager.getCredential(activity, fallbackRequest)
            }

            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                withContext(NonCancellable) {
                    Firebase.auth.signInWithCredential(authCredential).await()
                }
                onSuccess()
            } else {
                onError("Unexpected credential response type: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            val detail = e.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: e.message.orEmpty()
            Log.w("DevicesAuth", "Google Sign-In cancelled/aborted: $detail", e)
            val userMsg = if (detail.contains("developer", ignoreCase = true) ||
                detail.contains("10:", ignoreCase = true) ||
                detail.contains("28444", ignoreCase = true) ||
                detail.contains("caller", ignoreCase = true)
            ) {
                "Google OAuth rejected this APK build ($detail). Verify the APK signing SHA-1 & OAuth consent settings."
            } else {
                "Sign-in cancelled. If this occurred right after tapping an account, ensure this APK's SHA-1 matches Firebase & OAuth consent settings."
            }
            onError(userMsg)
        } catch (e: NoCredentialException) {
            Log.w("DevicesAuth", "No Google credentials available: ${e.errorMessage}", e)
            onError("No Google account available on this device. Add a Google account in Android Settings.")
        } catch (e: GetCredentialException) {
            Log.e("DevicesAuth", "CredentialManager failed (${e.type}): ${e.errorMessage}", e)
            onError("Google Sign-In failed (${e.type}): ${e.errorMessage ?: e.localizedMessage ?: "Unknown error"}")
        } catch (e: Exception) {
            Log.e("DevicesAuth", "Firebase Google Sign-In failed", e)
            onError(e.localizedMessage ?: "Google sign-in failed.")
        }
    }
}

fun performSignOut(
    context: Context,
    scope: CoroutineScope,
    onBeforeSignOut: (suspend () -> Unit)? = null
) {
    val credentialManager = CredentialManager.create(context)
    scope.launch {
        withContext(NonCancellable) {
            try {
                onBeforeSignOut?.invoke()
            } catch (e: Exception) {
                Log.w("DevicesAuth", "Pre-sign-out cleanup warning: ${e.message}")
            }
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("DevicesAuth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
            }
        }
    }
}
