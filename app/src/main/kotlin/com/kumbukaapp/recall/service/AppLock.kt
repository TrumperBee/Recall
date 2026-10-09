package com.kumbukaapp.recall.service

import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/** Biometric app lock for Recall */
object AppLock {

    private var biometricPrompt: BiometricPrompt? = null
    private var promptInfo: BiometricPrompt.PromptInfo? = null
    private var isLocked = false
    private var autoLockTimeoutMs: Long = 5 * 60 * 1000L // 5 minutes default

    private val handler = Handler(Looper.getMainLooper())
    private var autoLockRunnable: Runnable? = null

    private var pendingSuccess: (() -> Unit)? = null
    private var pendingFailure: (() -> Unit)? = null

    /** Initialize biometric prompt for an activity */
    fun init(activity: FragmentActivity) {
        val executor = Executor { it.run() }

        biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isLocked = false
                    cancelAutoLock()
                    pendingSuccess?.invoke()
                    pendingSuccess = null
                    pendingFailure = null
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    isLocked = true
                    pendingFailure?.invoke()
                    pendingSuccess = null
                    pendingFailure = null
                }

                override fun onAuthenticationFailed() {
                    isLocked = true
                    pendingFailure?.invoke()
                    pendingSuccess = null
                    pendingFailure = null
                }
            }
        )

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Recall Lock")
            .setDescription("Authenticate to access your private calls")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL or
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
    }

    /** Check if the app is currently locked */
    fun isAppLocked(): Boolean = isLocked

    /** Authenticate biometrics (call from onCreate or onResume) */
    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFailed: () -> Unit) {
        if (biometricPrompt == null) {
            init(activity)
        }

        pendingSuccess = onSuccess
        pendingFailure = onFailed
        isLocked = true
        startAutoLock()
        biometricPrompt?.authenticate(promptInfo!!)
    }

    /** Lock the app (call onPause or when going to background) */
    fun lock() {
        isLocked = true
        startAutoLock()
    }

    /** Unlock the app */
    fun unlock() {
        isLocked = false
        cancelAutoLock()
    }

    /** Set auto-lock timeout */
    fun setAutoLockTimeout(timeoutMs: Long) {
        autoLockTimeoutMs = timeoutMs
        cancelAutoLock()
        startAutoLock()
    }

    private fun startAutoLock() {
        cancelAutoLock()
        val runnable = Runnable { isLocked = true }
        autoLockRunnable = runnable
        handler.postDelayed(runnable, autoLockTimeoutMs)
    }

    private fun cancelAutoLock() {
        autoLockRunnable?.let { handler.removeCallbacks(it) }
        autoLockRunnable = null
    }
}
