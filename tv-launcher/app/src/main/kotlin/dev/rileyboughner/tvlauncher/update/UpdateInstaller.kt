package dev.rileyboughner.tvlauncher.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the APK named by [UpdateInfo.apkUrl] into the app cache (plain
 * `HttpURLConnection` — no DownloadManager, no extra HTTP-client dependency
 * needed for one file), then hands it to the system installer via a
 * FileProvider `content://` URI. Called from the [UpdatePrompt] composable.
 */
object UpdateInstaller {

    private const val TIMEOUT_MS = 15_000
    private const val APK_FILE_NAME = "update.apk"

    suspend fun downloadApk(context: Context, apkUrl: String): File = withContext(Dispatchers.IO) {
        val connection = URL(apkUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        val outFile = File(context.cacheDir, APK_FILE_NAME)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("APK download failed with HTTP $code")
            }
            connection.inputStream.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        outFile
    }

    /**
     * Downloads the APK and hands it to the system installer. Returns true
     * when the installer was launched, false when Android blocks the
     * install because this app isn't allowed to install unknown apps yet
     * (callers show the unknown-sources prompt in that case). Throws on
     * download failure.
     */
    suspend fun downloadAndInstall(context: Context, apkUrl: String): Boolean {
        val apkFile = downloadApk(context, apkUrl)
        if (!canInstallPackages(context)) return false
        context.startActivity(installIntent(context, apkFile))
        return true
    }

    /** Per-app "install unknown apps" grant — required alongside the manifest's REQUEST_INSTALL_PACKAGES permission. */
    fun canInstallPackages(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Sends Riley to the per-app "Allow from this source" toggle for this app specifically. */
    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
