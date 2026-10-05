package com.example.recoverx.scanner

import android.content.Context
import android.os.SystemClock
import com.example.recoverx.security.ScanPasswordManager

class ScanNotAuthorizedException : SecurityException("Scan not authorized")

/** Set by Home before navigating; reset when the Scan route is left. */
object ScanModeHolder {
    @Volatile var deep: Boolean = false
}

/**
 * Session token. ScannerCoordinator.deepScan() refuses to run without it, so every entry
 * point (UI, ViewModel, worker, service) must pass through the password gate first.
 */
object ScanAuthorization {
    private const val WINDOW_MS = 15 * 60 * 1000L
    @Volatile private var grantedAt = 0L

    fun grant() { grantedAt = SystemClock.elapsedRealtime() }
    fun revoke() { grantedAt = 0L }

    /** Grants only when NO password exists. Returns true if the scan may proceed. */
    fun grantIfNoPassword(context: Context): Boolean {
        return if (!ScanPasswordManager.get(context).hasPassword()) {
            grant(); true
        } else {
            revoke(); false
        }
    }

    fun requireAuthorized() {
        val t = grantedAt
        if (t == 0L || SystemClock.elapsedRealtime() - t > WINDOW_MS) throw ScanNotAuthorizedException()
    }
}