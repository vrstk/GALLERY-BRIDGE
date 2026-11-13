package com.gallerybridge.app.transfer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.gallerybridge.app.MainActivity
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.network.Services
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-visible, user-started transfer independent from Activity lifecycle.
 * Android 15+ imposes a cumulative background dataSync FGS runtime limit.
 * Future versions should use User-Initiated Data Transfer jobs for large queues.
 */
class TransferService : Service() {
    companion object {
        private const val CHANNEL = "gallery_bridge_transfers"
        private const val NOTIFICATION_ID = 4101
        private const val FINISHED_ID = 4102
        private const val ACTION_START = "com.gallerybridge.transfer.START"
        private const val ACTION_CANCEL = "com.gallerybridge.transfer.CANCEL"
        private const val EXTRA_FILES = "files"
        private const val EXTRA_MODEL = "model"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DATE = "date"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_EMAIL = "email"
        private const val EXTRA_PASSWORD = "password"
        private const val EXTRA_ROOT = "root"
        private const val EXTRA_TOKEN = "token"

        const val PREFS = "transfer_state"
        const val KEY_RUNNING = "running"
        const val KEY_STATUS = "status"
        const val KEY_DETAILS = "details"
        const val KEY_REVISION = "revision"

        fun begin(
            context: Context,
            files: List<Media>,
            model: String,
            title: String,
            date: String,
            target: String,
            email: String,
            password: String,
            root: String,
            token: String?
        ) {
            val array = JSONArray()
            for (file in files) {
                array.put(JSONObject().put("url", file.url).put("name", file.name)
                    .put("kind", file.kind.name).put("source", file.source))
            }
            val intent = Intent(context, TransferService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_FILES, array.toString())
                .putExtra(EXTRA_MODEL, model)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_DATE, date)
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_EMAIL, email)
                .putExtra(EXTRA_PASSWORD, password)
                .putExtra(EXTRA_ROOT, root)
                .putExtra(EXTRA_TOKEN, token)
            context.startForegroundService(intent)
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, TransferService::class.java).setAction(ACTION_CANCEL))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var cancelRequested = false
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Передача файлов", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Прогресс скачивания и загрузки в облако"
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelRequested = true
            job?.cancel()
            save(false, "Загрузка остановлена пользователем", "")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY

        // Must promote immediately after startForegroundService(), before any I/O.
        showForeground("Начинаем передачу", 0, 0)
        cancelRequested = false
        save(true, "Подготовка к фоновой передаче", "")

        job = scope.launch {
            try {
                val src = intent ?: error("Не указано задание")
                val data = JSONArray(src.getStringExtra(EXTRA_FILES) ?: "[]")
                val files = buildList {
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        add(Media(item.getString("url"), item.getString("name"),
                            Kind.valueOf(item.getString("kind")), item.optString("source")))
                    }
                }
                require(files.isNotEmpty()) { "Нет выбранных файлов" }
                val result = Services.transfer(
                    applicationContext, files,
                    src.getStringExtra(EXTRA_MODEL).orEmpty(),
                    src.getStringExtra(EXTRA_TITLE).orEmpty(),
                    src.getStringExtra(EXTRA_DATE).orEmpty(),
                    src.getStringExtra(EXTRA_TARGET).orEmpty(),
                    src.getStringExtra(EXTRA_EMAIL).orEmpty(),
                    src.getStringExtra(EXTRA_PASSWORD).orEmpty(),
                    src.getStringExtra(EXTRA_ROOT).orEmpty(),
                    src.getStringExtra(EXTRA_TOKEN)
                ) { message ->
                    save(true, message, "")
                    val match = Regex("^(\\d+)/(\\d+)").find(message)
                    val done = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val total = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
                    showProgress(done, total)
                }
                val summary = result.substringBefore('\n')
                val details = result.substringAfter('\n', "")
                save(false, summary, details)
                notificationManager.notify(FINISHED_ID, buildNotification(
                    "Передача завершена", summary, false, 0, 0
                ))
            } catch (e: CancellationException) {
                if (!cancelRequested) save(false, "Передача прервана системой", "Открой приложение и повтори загрузку недостающих файлов.")
            } catch (e: Exception) {
                save(false, "Ошибка фоновой передачи", e.message ?: e.javaClass.simpleName)
                notificationManager.notify(FINISHED_ID, buildNotification(
                    "Ошибка передачи", "Открой GALLERY BRIDGE для отчёта", false, 0, 0
                ))
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        // Do not persist credentials in START_REDELIVER_INTENT on process restart.
        return START_NOT_STICKY
    }

    private fun save(running: Boolean, status: String, details: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, running)
            .putString(KEY_STATUS, status)
            .putString(KEY_DETAILS, details)
            .putLong(KEY_REVISION, System.currentTimeMillis())
            .apply()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(
        title: String,
        message: String,
        running: Boolean,
        done: Int,
        total: Int
    ): Notification {
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openAppIntent())
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(running)
        if (running) {
            builder.setProgress(total, done, total == 0)
            val stop = PendingIntent.getService(
                this, 1, Intent(this, TransferService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stop)
        }
        return builder.build()
    }

    private fun showForeground(text: String, done: Int, total: Int) {
        val n = buildNotification("GALLERY BRIDGE", text, true, done, total)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun showProgress(done: Int, total: Int) {
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification("GALLERY BRIDGE", if (total > 0) "Файл $done из $total" else "Передача", true, done, total)
        )
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelRequested = true
        job?.cancel()
        save(false, "Лимит фоновой передачи Android исчерпан", "Повтори оставшиеся файлы после возвращения в приложение.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
