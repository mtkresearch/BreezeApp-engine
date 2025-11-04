package com.mtkresearch.breezeapp.engine.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import android.util.Log
import android.util.LruCache
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * SignatureValidator provides signature-based authorization for service binding.
 *
 * This class verifies that calling applications have authorized signing certificates,
 * implementing defense-in-depth security beyond Android's automatic signature protection.
 *
 * Performance: Signature verification completes in <10ms (NFR1 requirement)
 * Caching: Results cached for 5 minutes to optimize repeated checks
 * Audit: All unauthorized attempts logged with 30-day retention
 *
 * @author BreezeApp Team
 * @since 1.0.0
 */
object SignatureValidator {

    private const val TAG = "SignatureValidator"

    /**
     * Authorized signing certificate SHA-256 fingerprints.
     *
     * Add certificate hashes for all authorized applications:
     * - BreezeApp (main client)
     * - BreezeApp Dot (voice-first client)
     * - Third-party authorized apps
     *
     * To get certificate hash:
     * ```bash
     * keytool -printcert -file app-signing-cert.der | grep SHA256
     * ```
     *
     * Format: Colon-separated hex string (e.g., "AB:CD:EF:01:23:...")
     */
    private val AUTHORIZED_SIGNATURES = setOf(
        // TODO: Replace with actual certificate hashes from Play App Signing
        // Example: "AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89"

        // For development/testing: Allow debug signatures
        // Remove this in production builds
        "DEBUG_CERTIFICATE_HASH_PLACEHOLDER"
    )

    /**
     * LRU cache for signature verification results.
     * Key: UID, Value: Verification result (true/false)
     * Max size: 50 entries (sufficient for typical app ecosystems)
     */
    private val signatureCache = LruCache<Int, CachedResult>(50)

    /**
     * Cache entry with timestamp for expiration
     */
    private data class CachedResult(
        val isValid: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            val age = System.currentTimeMillis() - timestamp
            return age > CACHE_TIMEOUT_MS
        }
    }

    /**
     * Cache timeout: 5 minutes
     * Balances performance with security (certificate changes rare but possible)
     */
    private const val CACHE_TIMEOUT_MS = 5 * 60 * 1000L

    /**
     * Audit log configuration
     */
    private const val AUDIT_LOG_FILE = "audit_security.log"
    private const val MAX_LOG_SIZE_BYTES = 10 * 1024 * 1024  // 10 MB
    private const val LOG_RETENTION_DAYS = 30

    /**
     * Returns the set of authorized signing certificate hashes.
     *
     * This method is primarily for testing and debugging. Production code
     * should not need to access this directly.
     *
     * @return Immutable set of authorized SHA-256 certificate hashes
     */
    fun getAuthorizedSignatures(): Set<String> {
        return AUTHORIZED_SIGNATURES.toSet()  // Return defensive copy
    }

    /**
     * Verifies that the calling application has an authorized signing certificate.
     *
     * This method performs the following checks:
     * 1. Check cache for recent verification result
     * 2. Get calling app's package names from UID
     * 3. Extract signing certificates for each package
     * 4. Compute SHA-256 hash of certificates
     * 5. Compare against authorized signatures
     * 6. Cache result and return
     *
     * Performance target: <10ms (measured and logged if exceeded)
     *
     * @param context Application context for PackageManager access
     * @param callingUid UID of the calling application (from Binder.getCallingUid())
     * @return true if caller has authorized signature, false otherwise
     */
    fun verifyCallerSignature(context: Context, callingUid: Int): Boolean {
        val startTime = System.nanoTime()

        try {
            // Check cache first for performance
            val cached = signatureCache.get(callingUid)
            if (cached != null && !cached.isExpired()) {
                Log.d(TAG, "Cache hit for UID $callingUid: ${cached.isValid}")
                return cached.isValid
            }

            // Perform verification
            val isValid = performSignatureVerification(context, callingUid)

            // Cache result
            signatureCache.put(callingUid, CachedResult(isValid))

            // Log unauthorized attempts
            if (!isValid) {
                logUnauthorizedAttempt(context, callingUid)
            }

            return isValid

        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed with exception", e)
            return false
        } finally {
            // Performance monitoring
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            if (durationMs > 10) {
                Log.w(TAG, "Signature verification took ${durationMs}ms (exceeds 10ms target)")
            } else {
                Log.d(TAG, "Signature verification completed in ${durationMs}ms")
            }
        }
    }

    /**
     * Performs the actual signature verification logic.
     *
     * @param context Application context
     * @param callingUid UID of calling app
     * @return true if signature matches authorized set
     */
    private fun performSignatureVerification(context: Context, callingUid: Int): Boolean {
        val packageManager = context.packageManager

        // Get all packages for this UID (usually just one, but can be multiple)
        val packages = packageManager.getPackagesForUid(callingUid)
        if (packages == null || packages.isEmpty()) {
            Log.w(TAG, "No packages found for UID $callingUid")
            return false
        }

        // Check each package (any match = authorized)
        return packages.any { packageName ->
            try {
                verifyPackageSignature(packageManager, packageName)
            } catch (e: NameNotFoundException) {
                Log.e(TAG, "Package not found: $packageName", e)
                false
            }
        }
    }

    /**
     * Verifies signature for a specific package.
     *
     * @param packageManager PackageManager instance
     * @param packageName Package to verify
     * @return true if package has authorized signature
     * @throws NameNotFoundException if package doesn't exist
     */
    private fun verifyPackageSignature(
        packageManager: PackageManager,
        packageName: String
    ): Boolean {
        Log.d(TAG, "Verifying signature for package: $packageName")

        // Get package info with signatures
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9+ (API 28+): Use GET_SIGNING_CERTIFICATES
            packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            // Android 8.1 and below: Use deprecated GET_SIGNATURES
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES
            )
        }

        // Extract signatures based on Android version
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use signingInfo for Android 9+
            val signingInfo = packageInfo.signingInfo
            if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                null
            }
        } else {
            // Use legacy signatures for older Android
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        if (signatures == null || signatures.isEmpty()) {
            Log.w(TAG, "No signatures found for $packageName")
            return false
        }

        // Check if any signature matches authorized set
        return signatures.any { signature ->
            val certBytes = signature.toByteArray()
            val hash = computeSHA256(certBytes)

            val isAuthorized = hash in AUTHORIZED_SIGNATURES
            if (isAuthorized) {
                Log.i(TAG, "✅ Authorized signature found for $packageName")
            } else {
                Log.w(TAG, "❌ Unauthorized signature for $packageName: $hash")
            }

            isAuthorized
        }
    }

    /**
     * Computes SHA-256 hash of certificate data.
     *
     * @param data Certificate bytes
     * @return SHA-256 hash as colon-separated hex string
     */
    private fun computeSHA256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString(":") { byte ->
            "%02X".format(byte)
        }
    }

    /**
     * Logs unauthorized binding attempt for security auditing.
     *
     * Log format: JSON with timestamp, UID, package name, signature, result
     * Retention: 30 days (automatic rotation)
     *
     * @param context Application context
     * @param callingUid UID of unauthorized caller
     */
    fun logUnauthorizedAttempt(context: Context, callingUid: Int) {
        try {
            val packageManager = context.packageManager
            val packages = packageManager.getPackagesForUid(callingUid) ?: arrayOf("UNKNOWN")
            val packageName = packages.firstOrNull() ?: "UNKNOWN"

            // Get signature hash for logging
            val signatureHash = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val packageInfo = packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )
                    val signature = packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
                    signature?.let { computeSHA256(it.toByteArray()) } ?: "UNKNOWN"
                } else {
                    @Suppress("DEPRECATION")
                    val packageInfo = packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNATURES
                    )
                    val signature = packageInfo.signatures?.firstOrNull()
                    signature?.let { computeSHA256(it.toByteArray()) } ?: "UNKNOWN"
                }
            } catch (e: Exception) {
                "ERROR: ${e.message}"
            }

            // Create JSON log entry
            val logEntry = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("event", "UNAUTHORIZED_BINDING")
                put("callingUid", callingUid)
                put("packageName", packageName)
                put("signatureHash", signatureHash)
                put("result", "DENIED")
            }

            // Write to log file
            writeAuditLog(context, logEntry.toString())

            // Rotate logs if needed
            rotateLogsIfNeeded(context)

            Log.i(TAG, "Logged unauthorized attempt: $packageName (UID: $callingUid)")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to log unauthorized attempt", e)
        }
    }

    /**
     * Writes audit log entry to file.
     *
     * @param context Application context
     * @param entry JSON log entry
     */
    private fun writeAuditLog(context: Context, entry: String) {
        val logFile = File(context.filesDir, AUDIT_LOG_FILE)

        try {
            FileWriter(logFile, true).use { writer ->
                writer.appendLine(entry)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audit log", e)
        }
    }

    /**
     * Rotates audit logs if they exceed maximum size.
     *
     * Old logs are archived with timestamp, and a new log file is started.
     * Logs older than 30 days are deleted.
     *
     * @param context Application context
     */
    private fun rotateLogsIfNeeded(context: Context) {
        val logFile = File(context.filesDir, AUDIT_LOG_FILE)

        try {
            // Check size
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
                val archiveName = "audit_security_${System.currentTimeMillis()}.log"
                val archiveFile = File(context.filesDir, archiveName)

                // Rename current log to archive
                if (logFile.renameTo(archiveFile)) {
                    Log.i(TAG, "Rotated audit log to $archiveName")
                }
            }

            // Clean up old archives (>30 days)
            cleanupOldLogs(context)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate logs", e)
        }
    }

    /**
     * Deletes audit log files older than retention period.
     *
     * @param context Application context
     */
    private fun cleanupOldLogs(context: Context) {
        val filesDir = context.filesDir
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(LOG_RETENTION_DAYS.toLong())

        filesDir.listFiles { file ->
            file.name.startsWith("audit_security_") && file.name.endsWith(".log")
        }?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    Log.i(TAG, "Deleted old audit log: ${file.name}")
                }
            }
        }
    }

    /**
     * Clears the signature verification cache.
     *
     * This should be called rarely, typically only when:
     * - Certificate configuration changes
     * - Testing scenarios require cache reset
     * - Security incident requires immediate re-verification
     */
    fun clearCache() {
        signatureCache.evictAll()
        Log.i(TAG, "Signature verification cache cleared")
    }

    /**
     * Returns cache statistics for monitoring and debugging.
     *
     * @return Map with cache stats: size, hitRate, etc.
     */
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "size" to signatureCache.size(),
            "maxSize" to signatureCache.maxSize(),
            "hitCount" to signatureCache.hitCount(),
            "missCount" to signatureCache.missCount(),
            "evictionCount" to signatureCache.evictionCount()
        )
    }
}
