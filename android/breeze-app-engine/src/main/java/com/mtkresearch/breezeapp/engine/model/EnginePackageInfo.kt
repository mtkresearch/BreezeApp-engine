package com.mtkresearch.breezeapp.engine.model

/**
 * Metadata about the BreezeApp-engine package installation.
 *
 * @param packageName Always "com.mtkresearch.breezeapp.engine"
 * @param versionName Human-readable version (e.g., "1.5.0")
 * @param versionCode Numeric version code for comparison
 * @param isInstalled Quick boolean check
 * @param installTime When package was first installed (from PackageInfo)
 * @param updateTime When package was last updated (from PackageInfo)
 */
data class EnginePackageInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isInstalled: Boolean,
    val installTime: Long? = null,
    val updateTime: Long? = null
) {
    init {
        require(packageName.isNotEmpty()) { "Package name cannot be empty" }
        if (isInstalled) {
            require(versionName.isNotEmpty()) { "Version name required when installed" }
            require(versionCode > 0) { "Version code must be positive when installed" }
        }
        installTime?.let { require(it <= System.currentTimeMillis()) { "Install time cannot be in future" } }
        updateTime?.let { require(it <= System.currentTimeMillis()) { "Update time cannot be in future" } }
    }

    companion object {
        const val ENGINE_PACKAGE_NAME = "com.mtkresearch.breezeapp.engine"
    }
}
