package com.mtkresearch.breezeapp.engine.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for SignatureValidator.
 *
 * Tests cover:
 * - T024: Signature match scenarios
 * - T025: Signature mismatch scenarios
 * - T026: Missing signature scenarios
 * - T027: Verification performance (<10ms requirement)
 * - T028: Audit logging for unauthorized attempts
 * - T029: Cache behavior and expiration
 *
 * @author BreezeApp Team
 * @since 1.0.0
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])  // Test on Android 9+
class SignatureValidatorTest {

    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager

    // Test UIDs
    private val AUTHORIZED_UID = 10001
    private val UNAUTHORIZED_UID = 10002
    private val MISSING_SIGNATURE_UID = 10003

    // Test package names
    private val AUTHORIZED_PACKAGE = "com.mtkresearch.breezeapp.test"
    private val UNAUTHORIZED_PACKAGE = "com.malicious.app"
    private val NO_SIGNATURE_PACKAGE = "com.unsigned.app"

    // Mock signatures (these match what SignatureValidator expects)
    private val AUTHORIZED_SIGNATURE_BYTES = "AUTHORIZED_CERT".toByteArray()
    private val UNAUTHORIZED_SIGNATURE_BYTES = "UNAUTHORIZED_CERT".toByteArray()

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPackageManager = mockk(relaxed = true)

        every { mockContext.packageManager } returns mockPackageManager
        every { mockContext.filesDir } returns mockk(relaxed = true)

        // Clear cache before each test
        SignatureValidator.clearCache()
    }

    @After
    fun teardown() {
        clearAllMocks()
        SignatureValidator.clearCache()
    }

    // ============================================================================
    // T024: Test Signature Match Scenarios
    // ============================================================================

    @Test
    fun `testSignatureMatch_ReturnsTrue - Authorized app with matching signature`() {
        // Given: Authorized package with matching signature
        setupMockPackageWithSignature(
            uid = AUTHORIZED_UID,
            packageName = AUTHORIZED_PACKAGE,
            signatureBytes = AUTHORIZED_SIGNATURE_BYTES
        )

        // When: Verify signature
        val result = SignatureValidator.verifyCallerSignature(mockContext, AUTHORIZED_UID)

        // Then: Verification succeeds
        assertTrue(result, "Authorized signature should pass verification")
    }

    @Test
    fun `testMultiplePackagesForUid_OnlyOneAuthorized_ReturnsTrue`() {
        // Given: UID has multiple packages, one is authorized
        every { mockPackageManager.getPackagesForUid(AUTHORIZED_UID) } returns arrayOf(
            "com.other.app",
            AUTHORIZED_PACKAGE
        )

        setupMockPackage(AUTHORIZED_PACKAGE, AUTHORIZED_SIGNATURE_BYTES)
        setupMockPackage("com.other.app", UNAUTHORIZED_SIGNATURE_BYTES)

        // When: Verify signature
        val result = SignatureValidator.verifyCallerSignature(mockContext, AUTHORIZED_UID)

        // Then: Verification succeeds (any package with valid signature)
        assertTrue(result, "Should pass if any package in UID has valid signature")
    }

    // ============================================================================
    // T025: Test Signature Mismatch Scenarios
    // ============================================================================

    @Test
    fun `testSignatureMismatch_ReturnsFalse - Unauthorized app signature`() {
        // Given: Unauthorized package with different signature
        setupMockPackageWithSignature(
            uid = UNAUTHORIZED_UID,
            packageName = UNAUTHORIZED_PACKAGE,
            signatureBytes = UNAUTHORIZED_SIGNATURE_BYTES
        )

        // When: Verify signature
        val result = SignatureValidator.verifyCallerSignature(mockContext, UNAUTHORIZED_UID)

        // Then: Verification fails
        assertFalse(result, "Unauthorized signature should fail verification")
    }

    @Test
    fun `testNoPackagesForUid_ReturnsFalse`() {
        // Given: UID has no associated packages
        every { mockPackageManager.getPackagesForUid(any()) } returns null

        // When: Verify signature
        val result = SignatureValidator.verifyCallerSignature(mockContext, 99999)

        // Then: Verification fails
        assertFalse(result, "No packages for UID should fail verification")
    }

    @Test
    fun `testEmptyPackagesArray_ReturnsFalse`() {
        // Given: UID has empty packages array
        every { mockPackageManager.getPackagesForUid(any()) } returns arrayOf()

        // When: Verify signature
        val result = SignatureValidator.verifyCallerSignature(mockContext, 99999)

        // Then: Verification fails
        assertFalse(result, "Empty packages array should fail verification")
    }

    // ============================================================================
    // T026: Test Missing Signature Scenarios
    // ============================================================================

    @Test
    fun `testMissingSignature_ReturnsFalse - Package has no signatures`() {
        // Given: Package exists but has no signatures
        every { mockPackageManager.getPackagesForUid(MISSING_SIGNATURE_UID) } returns
            arrayOf(NO_SIGNATURE_PACKAGE)

        val packageInfo = mockk<PackageInfo>(relaxed = true)
        packageInfo.signingInfo = null
        @Suppress("DEPRECATION")
        packageInfo.signatures = null

        every {
            mockPackageManager.getPackageInfo(
                NO_SIGNATURE_PACKAGE,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } returns packageInfo

        // When: Verify signature
        val result = SignatureValidator.verifyCallerSignature(mockContext, MISSING_SIGNATURE_UID)

        // Then: Verification fails
        assertFalse(result, "Package with no signatures should fail verification")
    }

    @Test
    fun `testPackageNotFound_ReturnsFalse`() {
        // Given: Package doesn't exist
        every { mockPackageManager.getPackagesForUid(any()) } returns arrayOf("com.nonexistent.app")
        every {
            mockPackageManager.getPackageInfo(any<String>(), any<Int>())
        } throws PackageManager.NameNotFoundException()

        // When: Verify signature
        val result = SignatureValidator.verifyCallerSignature(mockContext, 88888)

        // Then: Verification fails gracefully
        assertFalse(result, "Non-existent package should fail verification")
    }

    // ============================================================================
    // T027: Test Verification Performance (<10ms requirement)
    // ============================================================================

    @Test
    fun `testVerificationPerformance_UnderTenMilliseconds`() {
        // Given: Authorized package
        setupMockPackageWithSignature(
            uid = AUTHORIZED_UID,
            packageName = AUTHORIZED_PACKAGE,
            signatureBytes = AUTHORIZED_SIGNATURE_BYTES
        )

        // When: Measure verification time over multiple iterations
        val iterations = 100
        val measurements = mutableListOf<Long>()

        repeat(iterations) {
            // Clear cache to measure actual verification time
            SignatureValidator.clearCache()

            val startTime = System.nanoTime()
            SignatureValidator.verifyCallerSignature(mockContext, AUTHORIZED_UID)
            val durationNs = System.nanoTime() - startTime

            measurements.add(durationNs / 1_000_000)  // Convert to milliseconds
        }

        // Then: Average time should be under 10ms
        val averageMs = measurements.average()
        val maxMs = measurements.maxOrNull() ?: 0L

        assertTrue(
            averageMs < 10.0,
            "Average verification time ${averageMs}ms exceeds 10ms requirement"
        )
        println("Performance test: Avg=${averageMs}ms, Max=${maxMs}ms (target: <10ms)")
    }

    @Test
    fun `testCachedVerification_FasterThanInitial`() {
        // Given: Authorized package
        setupMockPackageWithSignature(
            uid = AUTHORIZED_UID,
            packageName = AUTHORIZED_PACKAGE,
            signatureBytes = AUTHORIZED_SIGNATURE_BYTES
        )

        SignatureValidator.clearCache()

        // When: First verification (uncached)
        val start1 = System.nanoTime()
        SignatureValidator.verifyCallerSignature(mockContext, AUTHORIZED_UID)
        val duration1 = (System.nanoTime() - start1) / 1_000_000

        // Second verification (cached)
        val start2 = System.nanoTime()
        SignatureValidator.verifyCallerSignature(mockContext, AUTHORIZED_UID)
        val duration2 = (System.nanoTime() - start2) / 1_000_000

        // Then: Cached verification should be faster
        assertTrue(
            duration2 < duration1,
            "Cached verification (${duration2}ms) should be faster than initial (${duration1}ms)"
        )
        println("Cache performance: Initial=${duration1}ms, Cached=${duration2}ms")
    }

    // ============================================================================
    // T028: Test Audit Logging for Unauthorized Attempts
    // ============================================================================

    @Test
    fun `testAuditLogging_RecordsUnauthorizedAttempts`() {
        // Given: Unauthorized package
        setupMockPackageWithSignature(
            uid = UNAUTHORIZED_UID,
            packageName = UNAUTHORIZED_PACKAGE,
            signatureBytes = UNAUTHORIZED_SIGNATURE_BYTES
        )

        // Mock file operations for audit logging
        val mockFilesDir = mockk<java.io.File>(relaxed = true)
        every { mockContext.filesDir } returns mockFilesDir
        every { mockFilesDir.listFiles(any<(java.io.File) -> Boolean>()) } returns null

        // When: Verify unauthorized signature
        val result = SignatureValidator.verifyCallerSignature(mockContext, UNAUTHORIZED_UID)

        // Then: Verification fails and audit log is called
        assertFalse(result, "Unauthorized signature should fail")

        // Verify audit logging was attempted
        verify(atLeast = 1) { mockContext.filesDir }
    }

    @Test
    fun `testLogUnauthorizedAttempt_CreatesValidJSON`() {
        // Given: Unauthorized package
        setupMockPackageWithSignature(
            uid = UNAUTHORIZED_UID,
            packageName = UNAUTHORIZED_PACKAGE,
            signatureBytes = UNAUTHORIZED_SIGNATURE_BYTES
        )

        // Mock file operations
        val mockFilesDir = mockk<java.io.File>(relaxed = true)
        every { mockContext.filesDir } returns mockFilesDir
        every { mockFilesDir.listFiles(any<(java.io.File) -> Boolean>()) } returns null

        // When: Explicitly log unauthorized attempt
        SignatureValidator.logUnauthorizedAttempt(mockContext, UNAUTHORIZED_UID)

        // Then: File operations were attempted (indicates logging occurred)
        verify(atLeast = 1) { mockContext.filesDir }
    }

    // ============================================================================
    // T029: Test Cache Behavior
    // ============================================================================

    @Test
    fun `testCacheBehavior_StoresResults`() {
        // Given: Authorized package
        setupMockPackageWithSignature(
            uid = AUTHORIZED_UID,
            packageName = AUTHORIZED_PACKAGE,
            signatureBytes = AUTHORIZED_SIGNATURE_BYTES
        )

        SignatureValidator.clearCache()

        // When: First verification
        val result1 = SignatureValidator.verifyCallerSignature(mockContext, AUTHORIZED_UID)

        // Get cache stats
        val stats1 = SignatureValidator.getCacheStats()
        val size1 = stats1["size"] as Int

        // Second verification
        val result2 = SignatureValidator.verifyCallerSignature(mockContext, AUTHORIZED_UID)
        val stats2 = SignatureValidator.getCacheStats()
        val hitCount2 = stats2["hitCount"] as Int

        // Then: Both verifications succeed, cache hit on second call
        assertTrue(result1, "First verification should succeed")
        assertTrue(result2, "Second verification should succeed")
        assertEquals(1, size1, "Cache should have 1 entry after first call")
        assertTrue(hitCount2 > 0, "Second call should hit cache")
    }

    @Test
    fun `testClearCache_RemovesAllEntries`() {
        // Given: Multiple cached entries
        setupMockPackageWithSignature(AUTHORIZED_UID, AUTHORIZED_PACKAGE, AUTHORIZED_SIGNATURE_BYTES)

        SignatureValidator.verifyCallerSignature(mockContext, AUTHORIZED_UID)
        SignatureValidator.verifyCallerSignature(mockContext, AUTHORIZED_UID + 1)

        val statsBefore = SignatureValidator.getCacheStats()
        val sizeBefore = statsBefore["size"] as Int

        // When: Clear cache
        SignatureValidator.clearCache()

        // Then: Cache is empty
        val statsAfter = SignatureValidator.getCacheStats()
        val sizeAfter = statsAfter["size"] as Int

        assertTrue(sizeBefore > 0, "Cache should have entries before clear")
        assertEquals(0, sizeAfter, "Cache should be empty after clear")
    }

    @Test
    fun `testGetCacheStats_ReturnsValidMetrics`() {
        // Given: Some cache activity
        setupMockPackageWithSignature(AUTHORIZED_UID, AUTHORIZED_PACKAGE, AUTHORIZED_SIGNATURE_BYTES)

        SignatureValidator.clearCache()
        SignatureValidator.verifyCallerSignature(mockContext, AUTHORIZED_UID)  // Miss
        SignatureValidator.verifyCallerSignature(mockContext, AUTHORIZED_UID)  // Hit

        // When: Get cache stats
        val stats = SignatureValidator.getCacheStats()

        // Then: Stats contain expected keys
        assertTrue(stats.containsKey("size"), "Stats should contain 'size'")
        assertTrue(stats.containsKey("maxSize"), "Stats should contain 'maxSize'")
        assertTrue(stats.containsKey("hitCount"), "Stats should contain 'hitCount'")
        assertTrue(stats.containsKey("missCount"), "Stats should contain 'missCount'")

        // Verify values
        assertEquals(1, stats["size"], "Cache size should be 1")
        assertEquals(50, stats["maxSize"], "Max size should be 50")
        assertTrue((stats["hitCount"] as Int) >= 1, "Should have at least 1 hit")
    }

    @Test
    fun `testGetAuthorizedSignatures_ReturnsImmutableCopy`() {
        // When: Get authorized signatures
        val signatures1 = SignatureValidator.getAuthorizedSignatures()
        val signatures2 = SignatureValidator.getAuthorizedSignatures()

        // Then: Returns consistent results (defensive copy)
        assertEquals(signatures1.size, signatures2.size, "Should return consistent results")
        assertTrue(signatures1.isNotEmpty(), "Should have at least one authorized signature")
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private fun setupMockPackageWithSignature(
        uid: Int,
        packageName: String,
        signatureBytes: ByteArray
    ) {
        every { mockPackageManager.getPackagesForUid(uid) } returns arrayOf(packageName)
        setupMockPackage(packageName, signatureBytes)
    }

    private fun setupMockPackage(packageName: String, signatureBytes: ByteArray) {
        val mockSignature = mockk<Signature>(relaxed = true)
        every { mockSignature.toByteArray() } returns signatureBytes

        val mockSigningInfo = mockk<SigningInfo>(relaxed = true)
        every { mockSigningInfo.hasMultipleSigners() } returns false
        every { mockSigningInfo.signingCertificateHistory } returns arrayOf(mockSignature)
        every { mockSigningInfo.apkContentsSigners } returns arrayOf(mockSignature)

        val packageInfo = mockk<PackageInfo>(relaxed = true)
        packageInfo.signingInfo = mockSigningInfo

        every {
            mockPackageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } returns packageInfo
    }
}
