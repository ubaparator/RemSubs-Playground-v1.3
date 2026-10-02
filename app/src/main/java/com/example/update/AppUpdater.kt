package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Contents of update.json, which CI publishes next to RemSubs.apk on every GitHub release. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val sizeBytes: Long,
    val notes: String
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val apk: File) : UpdateState
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState
}

/**
 * Self-update from GitHub Releases: reads update.json, downloads the APK it points at, verifies
 * it and hands it to the system installer. CI signs every build with the same key, so Android
 * installs it as an update and keeps the app's data.
 */
object AppUpdater {
    private const val UPDATE_DIR = "updates"

    /** Injected by CI; empty for local builds, which then never self-update. */
    val manifestUrl: String get() = BuildConfig.UPDATE_MANIFEST_URL
    val isConfigured: Boolean get() = manifestUrl.isNotBlank()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Suppress("DEPRECATION")
    fun currentVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    /** Parses update.json; a relative apkUrl is resolved against [manifestUrl]. */
    fun parseManifest(json: String, manifestUrl: String): UpdateInfo {
        val o = JSONObject(json)
        val versionCode = o.getLong("versionCode")
        return UpdateInfo(
            versionCode = versionCode,
            versionName = o.optString("versionName").ifBlank { versionCode.toString() },
            apkUrl = URI(manifestUrl).resolve(o.getString("apkUrl")).toString(),
            sha256 = o.optString("sha256").takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT),
            sizeBytes = o.optLong("size", -1L),
            notes = o.optString("notes")
        )
    }

    suspend fun fetchLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(manifestUrl)
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "RemSubs-Updater")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("update.json alınamadı (HTTP ${response.code})")
            val body = response.body?.string() ?: throw IOException("update.json boş geldi")
            parseManifest(body, manifestUrl)
        }
    }

    /** Downloads and verifies the APK; a verified file from an earlier attempt is reused. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        val target = File(dir, "RemSubs-${info.versionCode}.apk")
        if (target.isFile && info.sha256 != null && sha256Of(target) == info.sha256) {
            verifyArchive(context, target)
            return@withContext target
        }
        dir.listFiles()?.forEach { it.delete() }

        val partial = File(dir, "${target.name}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(info.apkUrl).header("User-Agent", "RemSubs-Updater").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("APK indirilemedi (HTTP ${response.code})")
            val body = response.body ?: throw IOException("APK boş geldi")
            val total = body.contentLength().takeIf { it > 0L } ?: info.sizeBytes
            body.byteStream().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (total > 0L) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }

        val hash = digest.digest().toHex()
        if (info.sha256 != null && hash != info.sha256) {
            partial.delete()
            throw IOException("İndirilen APK bozuk (SHA-256 uyuşmuyor). Tekrar dene.")
        }
        if (!partial.renameTo(target)) throw IOException("İndirilen APK kaydedilemedi.")
        verifyArchive(context, target)
        target
    }

    /** Deletes downloaded APKs that are not newer than the installed version. */
    fun cleanup(context: Context) {
        val current = currentVersionCode(context)
        File(context.cacheDir, UPDATE_DIR).listFiles()?.forEach { file ->
            val code = file.name.removePrefix("RemSubs-").substringBefore('.').toLongOrNull()
            if (code == null || code <= current) file.delete()
        }
    }

    fun canRequestInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Settings page where the user allows RemSubs to install updates (Android 8+). */
    fun installPermissionIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun verifyArchive(context: Context, apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
        if (archive == null) {
            apk.delete()
            throw IOException("İndirilen dosya geçerli bir APK değil.")
        }
        if (archive.packageName != context.packageName) {
            apk.delete()
            throw IOException("İndirilen APK bu uygulamaya ait değil (${archive.packageName}).")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !hasSameSigner(context, archive)) {
            apk.delete()
            throw IOException(
                "Yeni sürüm farklı bir anahtarla imzalanmış, güncelleme olarak kurulamaz. " +
                    "Uygulamayı bir kez kaldırıp yeni APK'yı elle kurman gerekiyor."
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun hasSameSigner(context: Context, archive: PackageInfo): Boolean {
        val archiveSigners = archive.signingInfo?.apkContentsSigners ?: return true
        val installed = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners ?: return true
        return archiveSigners.toSet() == installed.toSet()
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { String.format(Locale.ROOT, "%02x", it) }
}
