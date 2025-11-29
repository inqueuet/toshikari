package com.valoser.toshikari

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * GitHub の最新リリースを確認し、アプリに新しいバージョンがある場合は通知するヘルパー。
 *
 * - 一定間隔（既定: 12 時間）で GitHub Releases API をポーリング。
 * - 取得した `tag_name` を端末にインストールされているアプリのバージョン名と比較し、新しい場合のみ通知。
 * - 同じバージョンでの重複通知は SharedPreferences に記録して抑止。
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val gson = Gson()

    /**
     * GitHub リリースを確認し、必要に応じて通知する。
     *
     * @param force true の場合はチェック間隔に関係なく即時実行。
     */
    suspend fun checkForUpdates(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - AppPreferences.getLastReleaseCheckAt(context) < CHECK_INTERVAL_MS) {
            return
        }
        AppPreferences.setLastReleaseCheckAt(context, now)

        val release = fetchLatestRelease() ?: return
        val latestVersion = normalizeVersion(release.tagName) ?: return
        val currentVersionRaw = currentVersionName() ?: return
        val currentVersion = normalizeVersion(currentVersionRaw) ?: currentVersionRaw

        if (!isNewerVersion(latestVersion, currentVersion)) {
            return
        }
        if (AppPreferences.getLastNotifiedVersion(context) == latestVersion) {
            return
        }

        if (notifyNewVersion(release, latestVersion)) {
            AppPreferences.setLastNotifiedVersion(context, latestVersion)
        }
    }

    // ========== 内部処理 ==========
    private suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", Ua.STRING)
            .build()

        return@withContext try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("AppUpdateChecker", "Latest release fetch failed: ${response.code}")
                    return@use null
                }
                val body = response.body?.string() ?: return@use null
                val json = gson.fromJson(body, JsonObject::class.java)
                val tag = json.get("tag_name")?.asString
                if (tag.isNullOrBlank()) return@use null

                val name = json.get("name")?.asString
                val htmlUrl = json.get("html_url")?.asString ?: DEFAULT_RELEASE_PAGE
                ReleaseInfo(tag, name, htmlUrl)
            }
        } catch (t: Throwable) {
            Log.w("AppUpdateChecker", "Latest release fetch error", t)
            null
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = parseVersion(latest)
        val currentParts = parseVersion(current)
        val max = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until max) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        // 数値部分が同じ場合は文字列が異なれば新しいとみなす（例: rc → stable）
        return latest != current
    }

    private fun parseVersion(raw: String): List<Int> {
        return normalizeVersion(raw)
            ?.split('.', '-', '_')
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: emptyList()
    }

    private fun normalizeVersion(raw: String?): String? {
        return raw
            ?.trim()
            ?.removePrefix("v")
            ?.removePrefix("V")
            ?.ifBlank { null }
    }

    private fun currentVersionName(): String? {
        val packageName = context.packageName
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val flags = PackageManager.PackageInfoFlags.of(0)
                context.packageManager.getPackageInfo(packageName, flags).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0).versionName
            }
        }.getOrNull()
    }

    private fun notifyNewVersion(release: ReleaseInfo, displayVersion: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.i("AppUpdateChecker", "Notifications disabled by user; skip update notice")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.i("AppUpdateChecker", "POST_NOTIFICATIONS not granted; skip update notice")
                return false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.update_notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlags()
        )

        val title = context.getString(R.string.update_available_title, displayVersion)
        val content = release.name?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.update_available_message)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            manager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrElse { error ->
            Log.w("AppUpdateChecker", "Failed to post update notification", error)
            false
        }
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private data class ReleaseInfo(
        val tagName: String,
        val name: String?,
        val htmlUrl: String,
    )

    companion object {
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 1001
        private val CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(12)
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/inqueuet/toshikari/releases/latest"
        private const val DEFAULT_RELEASE_PAGE = "https://github.com/inqueuet/toshikari/releases/latest"
    }
}
