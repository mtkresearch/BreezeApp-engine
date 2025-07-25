package com.mtkresearch.breezeapp.engine.util

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Utility class for copying assets to internal/external storage
 * Extracted from Sherpa TTS example for reuse across runners
 */
object AssetCopyUtil {
    private const val TAG = "AssetCopyUtil"

    /**
     * Copy assets directory to external files directory
     * @param context Application context
     * @param assetPath Path in assets folder
     * @return Absolute path of copied directory
     */
    fun copyAssetsToExternalFiles(context: Context, assetPath: String): String {
        Log.i(TAG, "Copying assets from $assetPath to external files")
        copyAssets(context, assetPath)
        val newDir = context.getExternalFilesDir(null)!!.absolutePath
        Log.i(TAG, "Assets copied to: $newDir")
        return newDir
    }

    /**
     * Copy assets directory to internal files directory
     * @param context Application context
     * @param assetPath Path in assets folder
     * @return Absolute path of copied directory
     */
    fun copyAssetsToInternalFiles(context: Context, assetPath: String): String {
        Log.i(TAG, "Copying assets from $assetPath to internal files")
        copyAssetsToInternal(context, assetPath)
        val newDir = context.filesDir.absolutePath
        Log.i(TAG, "Assets copied to: $newDir")
        return newDir
    }

    /**
     * Recursively copy assets to external files directory
     */
    private fun copyAssets(context: Context, path: String) {
        val assets: Array<String>?
        try {
            assets = context.assets.list(path)
            if (assets!!.isEmpty()) {
                copyFileToExternal(context, path)
            } else {
                val fullPath = "${context.getExternalFilesDir(null)}/$path"
                val dir = File(fullPath)
                dir.mkdirs()
                for (asset in assets.iterator()) {
                    val p: String = if (path == "") "" else path + "/"
                    copyAssets(context, p + asset)
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Failed to copy $path. $ex")
            throw ex
        }
    }

    /**
     * Recursively copy assets to internal files directory
     */
    private fun copyAssetsToInternal(context: Context, path: String) {
        val assets: Array<String>?
        try {
            assets = context.assets.list(path)
            if (assets!!.isEmpty()) {
                copyFileToInternal(context, path)
            } else {
                val fullPath = "${context.filesDir}/$path"
                val dir = File(fullPath)
                dir.mkdirs()
                for (asset in assets.iterator()) {
                    val p: String = if (path == "") "" else path + "/"
                    copyAssetsToInternal(context, p + asset)
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Failed to copy $path. $ex")
            throw ex
        }
    }

    /**
     * Copy single file to external files directory
     */
    private fun copyFileToExternal(context: Context, filename: String) {
        try {
            val istream = context.assets.open(filename)
            val newFilename = context.getExternalFilesDir(null).toString() + "/" + filename
            val ostream = FileOutputStream(newFilename)
            copyStream(istream, ostream)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy $filename to external, $ex")
            throw ex
        }
    }

    /**
     * Copy single file to internal files directory
     */
    private fun copyFileToInternal(context: Context, filename: String) {
        try {
            val istream = context.assets.open(filename)
            val newFilename = context.filesDir.toString() + "/" + filename
            val ostream = FileOutputStream(newFilename)
            copyStream(istream, ostream)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy $filename to internal, $ex")
            throw ex
        }
    }

    /**
     * Copy input stream to output stream
     */
    private fun copyStream(istream: java.io.InputStream, ostream: FileOutputStream) {
        val buffer = ByteArray(1024)
        var read = 0
        while (read != -1) {
            ostream.write(buffer, 0, read)
            read = istream.read(buffer)
        }
        istream.close()
        ostream.flush()
        ostream.close()
    }
}