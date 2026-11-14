package com.gallerybridge.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.gallerybridge.app.core.*
import com.gallerybridge.app.network.Services
import com.gallerybridge.app.network.IntegrityAudit
import com.gallerybridge.app.network.ImageQualityProbe
import com.gallerybridge.app.network.VideoAvailabilityProbe
import com.gallerybridge.app.transfer.TransferService
import com.gallerybridge.app.transfer.QueueStore
import com.gallerybridge.app.transfer.HistoryStore
import com.gallerybridge.app.security.SecureMailCredentials
import com.gallerybridge.app.ui.*
import androidx.compose.ui.Alignment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var url by mutableStateOf("")
    private var model by mutableStateOf("")
    private var title by mutableStateOf("")
    private var date by mutableStateOf("")
    private var email by mutableStateOf("")
    private var password by mutableStateOf("")
    private var driveId by mutableStateOf("1vWsFWxllS6aOh6a0iD58w30moPHiDP4T")
    private var treeUri by mutableStateOf("")
    private var treeName by mutableStateOf("Папка не выбрана")
    private var mailRoot by mutableStateOf("Models")
    private var target by mutableStateOf("Mail WebDAV")
    private var dark by mutableStateOf(true)
    private var working by mutableStateOf(false)
    private var scanning by mutableStateOf(false)
    private var status by mutableStateOf("Вставьте HTTPS-ссылку на галерею или файл.")
    private var items by mutableStateOf<List<Media>>(emptyList())
    private var selected by mutableStateOf<Set<String>>(emptySet())
    private var reveal by mutableStateOf(false)
    private var rememberMailPassword by mutableStateOf(false)
    private var showOtherMedia by mutableStateOf(false)
    private var mediaFilter by mutableStateOf<Kind?>(null)
    private var showDestination by mutableStateOf(false)
    private var uploadStatus by mutableStateOf("")
    private var recommendedUrls by mutableStateOf<Set<String>>(emptySet())
    private var imageProbeRunning by mutableStateOf(false)
    private var videoProbeRunning by mutableStateOf(false)
    private var videoProbeReport by mutableStateOf("")
    private var videoProbeProgress by mutableStateOf("")
    private var videoProbeResults by mutableStateOf<Map<String, VideoAvailabilityProbe.Result>>(emptyMap())
    private var videoProbeJob: Job? = null
    private var imageProbeProgress by mutableStateOf("")
    private var imageProbeStatus by mutableStateOf("")
    private var imageProbeResults by mutableStateOf<Map<String, ImageQualityProbe.Result>>(emptyMap())
    private var comparisonReport by mutableStateOf("")
    private var metadataReport by mutableStateOf("")

    private var imageProbeJob: Job? = null
    private var reportDetails by mutableStateOf("")
    private var showReportDetails by mutableStateOf(false)
    private var transferRunning by mutableStateOf(false)
    private var notificationToken: String? = null
    private var resumeRequest = false
    private var savedQueue by mutableStateOf<QueueStore.Snapshot?>(null)
    private var history by mutableStateOf<List<HistoryStore.Record>>(emptyList())
    private var screen by mutableStateOf(GalleryPage.HOME)
    private var settingsOrigin = GalleryPage.HOME
    private var settingsDestination by mutableStateOf("Mail WebDAV")
    private var settingsStatus by mutableStateOf("")
    private var transferStatus by mutableStateOf("")
    private var queueFilter by mutableStateOf("ALL")
    private var showCloseQueueConfirmation by mutableStateOf(false)
    private var auditing by mutableStateOf(false)
    private var auditProgress by mutableStateOf("")
    private var auditReport by mutableStateOf("")

    private val mediaPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        if (working || transferRunning || videoProbeRunning || imageProbeRunning) {
            status = "Дождитесь завершения текущей операции."
            return@registerForActivityResult
        }
        val imported = mutableListOf<Media>()
        var unsupported = 0
        for (uri in uris.take(LocalMediaImport.MAX_FILES)) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val media = LocalMediaImport.fromDocument(this, uri)
                if (media != null) imported.add(media) else unsupported++
            } catch (_: Exception) { unsupported++ }
        }
        if (imported.isNotEmpty()) {
            val existing = items.map { it.url }.toSet()
            val additions = imported.distinctBy { it.url }.filter { it.url !in existing }
            items = items + additions
            selected = selected + additions.map { it.url }
            recommendedUrls = recommendedUrls + additions.map { it.url }
            mediaFilter = null
            showOtherMedia = false
            screen = GalleryPage.DOWNLOADS
            if (title.isBlank()) title = "Импортированные файлы"
            if (model.isBlank()) model = "Без имени"
            status = "Добавлено локальных файлов: ${additions.size}."
        } else status = "Новых поддерживаемых файлов не выбрано."
        if (unsupported > 0) status += " Пропущено: $unsupported."
    }

    private fun pickLocalMedia() {
        if (!working && !transferRunning && !videoProbeRunning && !imageProbeRunning) {
            mediaPicker.launch(arrayOf("video/*", "image/*"))
        }
    }

    private val treePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val dir = DocumentFile.fromTreeUri(this, uri)
                require(dir != null && dir.canWrite()) { "Папка не разрешает запись" }
                treeUri = uri.toString()
                treeName = dir.name ?: "Выбранная папка"
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("treeUri", treeUri).apply()
                settingsStatus = "Выбрана папка: $treeName"
            } catch (e: Exception) {
                treeUri = ""
                treeName = "Нет доступа"
                settingsStatus = "Ошибка выбора папки: ${e.message}"
            }
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Notification permission is optional for running a dataSync foreground service.
        if (resumeRequest) beginResumeTransfer(notificationToken) else beginServiceTransfer(notificationToken)
        resumeRequest = false
        notificationToken = null
    }

    private val auth = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        try {
            finishGoogleAuthorization(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(r.data).accessToken)
        } catch (e: Exception) {
            failGoogleAuthorization(e.message ?: "Ошибка авторизации Google")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = getSharedPreferences("settings", MODE_PRIVATE)
        model = p.getString("model", "") ?: ""
        email = p.getString("email", "") ?: ""
        SecureMailCredentials.load(this, email)?.let {
            password = it
            rememberMailPassword = true
        }
        driveId = p.getString("driveId", driveId) ?: driveId
        treeUri = p.getString("treeUri", "") ?: ""
        if (treeUri.isNotBlank()) {
            try {
                treeName = DocumentFile.fromTreeUri(this, Uri.parse(treeUri))?.name ?: "Сохранённая папка"
            } catch (_: Exception) { treeName = "Папка недоступна" }
        }
        mailRoot = p.getString("mailRoot", "Models") ?: "Models"
        dark = p.getBoolean("dark", true)
        applySystemBars()
        target = savedInstanceState?.getString("transferTarget")
            ?: p.getString("transferTarget", target) ?: target
        settingsOrigin = savedInstanceState.galleryPage("settingsOrigin")
        settingsDestination = savedInstanceState?.getString("settingsDestination") ?: target
        screen = savedInstanceState.galleryPage("galleryPage")
        savedQueue = QueueStore.load(this)
        history = HistoryStore.load(this)
        transferRunning = getSharedPreferences(TransferService.PREFS, MODE_PRIVATE)
            .getBoolean(TransferService.KEY_RUNNING, false)
        val previousLink = savedInstanceState?.getString("galleryUrl")
        val sharedLink = intent?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.startsWith("https://") }
        (previousLink ?: sharedLink)?.let(::setGalleryUrl)
        if (previousLink != null) {
            model = savedInstanceState?.getString("galleryModel") ?: model
            title = savedInstanceState?.getString("galleryTitle") ?: title
            date = savedInstanceState?.getString("galleryDate") ?: date
        }
        setContent { GalleryTheme(dark) { Ui() } }
    }

    private fun Bundle?.galleryPage(key: String): GalleryPage =
        GalleryPage.entries.firstOrNull { it.name == this?.getString(key) } ?: GalleryPage.HOME

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("galleryPage", screen.name)
        outState.putString("settingsOrigin", settingsOrigin.name)
        outState.putString("settingsDestination", settingsDestination)
        outState.putString("transferTarget", target)
        outState.putString("galleryUrl", url)
        outState.putString("galleryModel", model)
        outState.putString("galleryTitle", title)
        outState.putString("galleryDate", date)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.startsWith("https://") }?.let {
            setGalleryUrl(it)
            screen = GalleryPage.HOME
        }
    }

    private fun applySystemBars() {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun setDarkTheme(enabled: Boolean) {
        dark = enabled
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("dark", enabled).apply()
        applySystemBars()
    }

    private fun openSettings(destination: String = target) {
        settingsOrigin = screen
        settingsDestination = destination
        settingsStatus = ""
        reveal = false
        screen = GalleryPage.SETTINGS
    }

    private fun backFromPage() {
        reveal = false
        screen = if (screen == GalleryPage.SETTINGS) settingsOrigin else GalleryPage.HOME
    }
    private fun verifyImageSizes() {
        if (imageProbeRunning || working || transferRunning) return
        val candidates = ImageProbePlan.candidates(items, selected)
        if (candidates.isEmpty()) {
            imageProbeStatus = "Выберите фотографии для проверки разрешения."
            return
        }
        imageProbeJob = scope.launch {
            imageProbeRunning = true
            imageProbeStatus = ""
            imageProbeResults = emptyMap()
            try {
                candidates.forEachIndexed { index, item ->
                    ensureActive()
                    imageProbeProgress = "Проверка разрешения ${index + 1} из ${candidates.size}: ${item.name}"
                    val result = ImageQualityProbe.inspect(item)
                    ensureActive()
                    imageProbeResults = imageProbeResults + (item.url to result)
                }
            } catch (_: CancellationException) {
                imageProbeStatus = "Проверка разрешения остановлена."
            } finally {
                imageProbeRunning = false
                imageProbeProgress = ""
                imageProbeJob = null
            }
        }
    }

    private fun compareImageVariants() {
        if (imageProbeRunning || working || transferRunning) return
        val pairs = VariantComparison.pairs(items, selected)
        if (pairs.isEmpty()) {
            comparisonReport = "Для выбранных файлов не найдены другие варианты с тем же именем основы и в той же папке CDN."
            return
        }
        comparisonReport = ""
        val candidates = VariantComparison.candidates(pairs)
        imageProbeJob = scope.launch {
            imageProbeRunning = true
            try {
                val measurements = imageProbeResults.toMutableMap()
                candidates.forEachIndexed { index, item ->
                    ensureActive()
                    imageProbeProgress = "Сравнение ${index + 1} из ${candidates.size}: ${item.name}"
                    val existing = measurements[item.url]
                    if (existing?.verified != true) {
                        val result = ImageQualityProbe.inspect(item)
                        ensureActive()
                        measurements[item.url] = result
                        imageProbeResults = measurements.toMap()
                    }
                }
                val actual = measurements.values.filter { it.verified }
                    .associate { it.url to VariantComparison.Dimensions(it.width!!, it.height!!) }
                val improvements = VariantComparison.improvements(pairs, actual)
                val comparable = VariantComparison.comparableCount(pairs, actual)
                comparisonReport = buildString {
                    append("Сопоставлено по реальным размерам $comparable из ${pairs.size} пар. ")
                    append("Более крупные альтернативы: ${improvements.size}.")
                    if (improvements.isNotEmpty()) {
                        append("\n")
                        improvements.take(12).forEach { found ->
                            append("\n• ${found.chosen.name} (${found.old.width}×${found.old.height}) → ")
                            append("${found.alternative.name} (${found.newer.width}×${found.newer.height})")
                        }
                    }
                    append("\nФайлы не заменены. Результат не доказывает максимальное качество на всём сайте.")
                }
            } catch (_: CancellationException) {
                comparisonReport = "Сравнение прервано. Выбранные фотографии не изменены."
            } finally {
                imageProbeRunning = false
                imageProbeProgress = ""
                imageProbeJob = null
            }
        }
    }

    private fun checkSelectedVideos() {
        if (videoProbeRunning || imageProbeRunning || working || transferRunning) return
        val candidates = VideoAvailabilityProbe.allCandidates(items)
            .filter { videoProbeResults[it.url]?.blocked != true }
        if (candidates.isEmpty()) {
            videoProbeReport = if (VideoAvailabilityProbe.summary(items, videoProbeResults).allBlocked)
                "Все найденные варианты видео отклонены сервером источника."
                else "Новых вариантов для проверки нет."
            return
        }
        videoProbeJob = scope.launch {
            videoProbeRunning = true
            videoProbeReport = ""
            try {
                var blocked = 0
                var uncertain = 0
                candidates.forEachIndexed { index, media ->
                    ensureActive()
                    videoProbeProgress = "Видео ${index + 1} из ${candidates.size}: ${media.name}"
                    val result = VideoAvailabilityProbe.inspect(media)
                    ensureActive()
                    videoProbeResults = videoProbeResults + (media.url to result)
                    if (result.blocked) {
                        selected = selected - media.url
                        blocked++
                    } else if (result.verdict == VideoAvailabilityProbe.Verdict.UNCERTAIN) {
                        uncertain++
                    }
                }
                val summary = VideoAvailabilityProbe.summary(items, videoProbeResults)
                videoProbeReport = buildString {
                    append("Всего вариантов: ${summary.total}; проверено: ${summary.checked}; ")
                    append("недоступно: ${summary.blocked}; ответ положительный: ${summary.accepted}; ")
                    append("не определено: ${summary.uncertain}.")
                    if (summary.notChecked > 0) append(" Ещё не проверено: ${summary.notChecked}.")
                    if (summary.allBlocked) append(" Ни один найденный вариант не разрешён сервером.")
                    append(" Недоступные варианты сняты с выделения. Положительный ответ не гарантирует скачивание.")
                }
            } catch (_: CancellationException) {
                videoProbeReport = "Проверка видео остановлена."
            } finally {
                videoProbeRunning = false
                videoProbeProgress = ""
                videoProbeJob = null
            }
        }
    }

    private fun cancelVideoCheck() {
        videoProbeJob?.cancel()
        VideoAvailabilityProbe.cancel()
    }

    private fun cancelImageSizes() {
        imageProbeJob?.cancel()
        ImageQualityProbe.cancel()
    }

    private fun setGalleryUrl(newUrl: String) {
        val changed = GalleryKeys.canonical(url) != GalleryKeys.canonical(newUrl) ||
            (newUrl.isBlank() && url.isNotBlank())
        url = newUrl
        if (!changed) return
        cancelImageSizes()
        cancelVideoCheck()
        videoProbeResults = emptyMap()
        videoProbeReport = ""
        items = emptyList()
        selected = emptySet()
        recommendedUrls = emptySet()
        imageProbeResults = emptyMap()
        imageProbeStatus = ""
        comparisonReport = ""
        showOtherMedia = false
        mediaFilter = null
        showDestination = false
        uploadStatus = ""
        model = ""
        title = ""
        date = ""
        metadataReport = ""
        val cached = GalleryMetadataStore.load(this, newUrl)
        if (cached != null) {
            model = cached.model
            title = cached.title
            date = cached.date
            metadataReport = "Восстановлены сохранённые данные для этой галереи."
        }
        status = if (newUrl.isBlank()) "Вставьте HTTPS-ссылку на галерею или файл." else "Запустите поиск по ссылке."
    }

    private fun saveGalleryMetadata() {
        metadataReport = try {
            GalleryMetadataStore.save(this, url, GalleryFields(model, title, date))
            "Данные сохранены для этой ссылки."
        } catch (e: Exception) {
            "Не удалось сохранить данные галереи: ${e.message}"
        }
    }

    private fun scan() {
        if (working || transferRunning || imageProbeRunning || videoProbeRunning || url.isBlank()) return
        val requestedUrl = url
        screen = GalleryPage.DOWNLOADS
        working = true
        scanning = true
        status = "Проверяем ссылку…"
        items = emptyList()
        selected = emptySet()
        recommendedUrls = emptySet()
        imageProbeResults = emptyMap()
        imageProbeStatus = ""
        videoProbeResults = emptyMap()
        videoProbeReport = ""
        comparisonReport = ""
        metadataReport = ""
        showOtherMedia = false
        mediaFilter = null
        showDestination = false
        uploadStatus = ""
        scope.launch {
            try {
                val scan = Services.scan(requestedUrl)
                val recommended = withContext(Dispatchers.Default) {
                    QualitySelector.recommended(scan.media)
                }
                if (requestedUrl != url) return@launch
                val saved = GalleryMetadataStore.load(this@MainActivity, requestedUrl)
                if (title.isBlank()) title = saved?.title?.takeIf { it.isNotBlank() } ?: scan.title
                if (date.isBlank()) date = saved?.date ?: scan.date
                if (model.isBlank()) model = saved?.model?.takeIf { it.isNotBlank() } ?: scan.suggestedModel
                metadataReport = listOfNotNull(
                    "Сохранённые данные восстановлены.".takeIf { saved != null },
                    "Проверьте имя модели, предложенное по заголовку страницы.".takeIf { scan.suggestedModel.isNotBlank() },
                    "Укажите дату, если она известна.".takeIf { date.isBlank() }
                ).joinToString(" ")
                recommendedUrls = recommended
                selected = recommended
                items = scan.media
                status = if (items.isEmpty()) "Медиафайлы по этой ссылке не найдены."
                    else "Найдено файлов: ${items.size}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestedUrl == url) status = "Ошибка поиска: ${e.message}"
            } finally {
                scanning = false
                working = false
            }
        }
    }
    private fun upload(token: String?) {
        if (transferRunning) {
            working = false
            uploadStatus = "Загрузка уже выполняется в фоне"
            return
        }
        reportDetails = ""
        showReportDetails = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationToken = token
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            beginServiceTransfer(token)
        }
    }

    private fun beginServiceTransfer(token: String?) {
        try {
            if (target == "Mail WebDAV" && rememberMailPassword && password.isNotBlank()) {
                SecureMailCredentials.save(this, email, password)
            }
            val chosen = items.filter { it.url in selected }
            TransferService.begin(
                this, chosen, model, title, date.trim(), target, email, password,
                if (target == "Google Drive") driveId else if (target == "Папка Android") treeUri else mailRoot, token
            )
            savedQueue = QueueStore.load(this)
            getSharedPreferences("settings", MODE_PRIVATE).edit().putString("transferTarget", target).apply()
            transferRunning = true
            showDestination = false
            screen = GalleryPage.QUEUE
            transferStatus = "Фоновая передача запущена"
        } catch (e: Exception) {
            uploadStatus = "Не удалось запустить фоновую передачу: ${e.message}"
        } finally {
            working = false
        }
    }
    private fun beginResumeTransfer(token: String?) {
        try {
            val queue = QueueStore.load(this) ?: error("Очередь не найдена")
            if (queue.target == "Папка Android") {
                require(queue.root.isNotBlank()) { "Выбранная папка Android отсутствует" }
            }
            if (queue.target == "Mail WebDAV") {
                if (email.isBlank() || password.isBlank()) {
                    error("Укажи почту и пароль приложения Mail для возобновления")
                }
                if (queue.account.isNotBlank() && queue.account != email.trim()) {
                    error("Сохранённая очередь принадлежит другому аккаунту Mail: ${queue.account}")
                }
                if (rememberMailPassword) SecureMailCredentials.save(this, email, password)
            }
            TransferService.resume(this, email, password, token)
            savedQueue = QueueStore.load(this)
            transferRunning = true
            screen = GalleryPage.QUEUE
            transferStatus = "Продолжаем сохранённую очередь"
        } catch (e: Exception) {
            transferStatus = "Не удалось возобновить загрузку. ${e.message}"
        } finally { working = false }
    }

    /** Close an unfinished queue only after an explicit confirmation. */
    private fun closeQueue() {
        val queue = QueueStore.load(this) ?: run {
            showCloseQueueConfirmation = false
            savedQueue = null
            return
        }
        val active = transferRunning || getSharedPreferences(TransferService.PREFS, MODE_PRIVATE)
            .getBoolean(TransferService.KEY_RUNNING, false)
        if (active) {
            showCloseQueueConfirmation = false
            transferStatus = "Сначала остановите выполняющуюся передачу."
            return
        }
        try {
            check(HistoryStore.record(this, queue, "Закрыта вручную")) {
                "Не удалось сохранить очередь в историю"
            }
            check(QueueStore.clearIfId(this, queue.id)) {
                "Очередь изменилась или не удалось её закрыть"
            }
            savedQueue = QueueStore.load(this)
            history = HistoryStore.load(this)
            showCloseQueueConfirmation = false
            reportDetails = ""
            transferStatus = ""
            screen = if (items.isEmpty()) GalleryPage.HOME else GalleryPage.DOWNLOADS
        } catch (e: Exception) {
            showCloseQueueConfirmation = false
            transferStatus = "Не удалось закрыть очередь: ${e.message}"
        }
    }

    private fun auditQueue() {
        val queue = QueueStore.load(this) ?: run {
            auditReport = "Очередь не найдена"; return
        }
        if (auditing || transferRunning || queue.remaining != 0) return
        auditing = true
        auditReport = ""
        auditProgress = "Запуск проверки..."
        scope.launch {
            try {
                val result = IntegrityAudit.check(this@MainActivity, queue) { i, n, name ->
                    withContext(Dispatchers.Main) {
                        auditProgress = "Проверка $i из $n: $name"
                    }
                }
                auditReport = result.summary()
            } catch (e: CancellationException) {
                auditReport = "Проверка прервана."
            } catch (e: Exception) {
                auditReport = "Проверка не выполнена: ${e.message}"
            } finally {
                auditing = false
                auditProgress = ""
            }
        }
    }

    private fun resumeUpload() {
        val queue = QueueStore.load(this) ?: return
        if (transferRunning || working || queue.remaining == 0) return
        if (queue.target != "Google Drive") {
            uploadResume(null)
        } else {
            resumeRequest = true
            beginGoogleAuthorization()
        }
    }

    private fun uploadResume(token: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            resumeRequest = true
            notificationToken = token
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else beginResumeTransfer(token)
    }

    private fun beginUpload() {
        if (working || transferRunning || videoProbeRunning || imageProbeRunning || selected.isEmpty()) return
        if (!GalleryKeys.validDate(date.trim())) {
            uploadStatus = "Укажите реальную дату в формате ГГГГ-ММ-ДД или оставьте поле пустым."
            return
        }
        if (model.isBlank() || title.isBlank()) {
            uploadStatus = "Заполните имя модели и название публикации."
            return
        }
        val setupError = when (target) {
            "Mail WebDAV" -> "Укажите почту и пароль приложения Mail.".takeIf { email.isBlank() || password.isBlank() }
            "Google Drive" -> "Укажите ID папки Google Drive.".takeIf { driveId.isBlank() }
            else -> "Выберите папку назначения.".takeIf { treeUri.isBlank() }
        }
        if (setupError != null) {
            uploadStatus = setupError
            return
        }
        uploadStatus = ""
        working = true
        val chosenVideos = items.filter {
            it.kind == Kind.VIDEO && it.url in selected && it.url.startsWith("https://")
        }
        if (chosenVideos.isEmpty()) {
            if (target == "Google Drive") beginGoogleAuthorization() else upload(null)
            return
        }
        // A manual availability check is optional. Before creating a real transfer
        // queue, verify the selected video URLs again: a prior result may be stale.
        scope.launch {
            try {
                val checked = mutableListOf<VideoAvailabilityProbe.Result>()
                for ((index, video) in chosenVideos.withIndex()) {
                    ensureActive()
                    uploadStatus = "Проверяем источник видео ${index + 1} из ${chosenVideos.size}..."
                    val result = VideoAvailabilityProbe.inspect(video)
                    ensureActive()
                    checked.add(result)
                    videoProbeResults = videoProbeResults + (result.url to result)
                }
                val blockedUrls = checked.filter { it.blocked }.map { it.url }.toSet()
                if (blockedUrls.isNotEmpty()) {
                    // Do not create a failed queue for files already rejected by
                    // their source. Keep any other choices for a later attempt.
                    selected = selected - blockedUrls
                    uploadStatus = "Источник отказал в скачивании ${blockedUrls.size} видео (например, HTTP 403). " +
                        "Они сняты с выделения. Очередь не создана. Проверьте другую разрешённую ссылку."
                    working = false
                    return@launch
                }
                if (checked.any { it.verdict == VideoAvailabilityProbe.Verdict.UNCERTAIN }) {
                    uploadStatus = "Доступ к части видео не подтверждён; при передаче возможна ошибка."
                } else {
                    uploadStatus = ""
                }
                // Headers are only an early check. The transfer still validates
                // actual bytes and HTTP status, without bypassing site restrictions.
                if (target == "Google Drive") beginGoogleAuthorization() else upload(null)
            } catch (_: CancellationException) {
                uploadStatus = "Проверка источника видео остановлена."
                working = false
            } catch (e: Exception) {
                uploadStatus = "Ошибка проверки видео: ${e.message ?: "нет ответа"}"
                working = false
            }
        }
    }

    private fun beginGoogleAuthorization() {
        working = true
        if (resumeRequest) transferStatus = "Запрашиваем доступ к Google Drive…"
        else uploadStatus = "Запрашиваем доступ к Google Drive…"
        Identity.getAuthorizationClient(this).authorize(
            AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive"))).build()
        ).addOnSuccessListener { a ->
            if (a.hasResolution()) {
                val p = a.pendingIntent
                if (p != null) auth.launch(IntentSenderRequest.Builder(p.intentSender).build())
                else failGoogleAuthorization("Google OAuth недоступен")
            } else {
                finishGoogleAuthorization(a.accessToken)
            }
        }.addOnFailureListener {
            val msg = it.message.orEmpty()
            val message = if (msg.contains("UNREGISTERED_ON_API_CONSOLE", true) || msg.contains("DEVELOPER_ERROR", true))
                "Google Drive: зарегистрируйте OAuth-клиент для пакета $packageName и SHA-1 установленной APK."
                else "Ошибка авторизации Google: $msg"
            failGoogleAuthorization(message)
        }
    }

    private fun finishGoogleAuthorization(token: String?) {
        if (token.isNullOrEmpty()) {
            failGoogleAuthorization("Google Drive не предоставил доступ")
        } else if (resumeRequest) {
            resumeRequest = false
            uploadResume(token)
        } else {
            upload(token)
        }
    }

    private fun failGoogleAuthorization(message: String) {
        if (resumeRequest) transferStatus = message else uploadStatus = message
        resumeRequest = false
        working = false
    }
    @Composable
    private fun Ui() {
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        val startSearch = {
            keyboard?.hide()
            focus.clearFocus()
            scan()
        }
        val searchAllowed = !working && !transferRunning && !imageProbeRunning && !videoProbeRunning
        BackHandler(enabled = screen != GalleryPage.HOME, onBack = ::backFromPage)
        LaunchedEffect(Unit) {
            var lastRevision = -1L
            while (isActive) {
                val p = getSharedPreferences(TransferService.PREFS, MODE_PRIVATE)
                val revision = p.getLong(TransferService.KEY_REVISION, 0L)
                if (revision > 0L && revision != lastRevision) {
                    lastRevision = revision
                    transferRunning = p.getBoolean(TransferService.KEY_RUNNING, false)
                    transferStatus = p.getString(TransferService.KEY_STATUS, "").orEmpty()
                    reportDetails = p.getString(TransferService.KEY_DETAILS, "").orEmpty()
                    savedQueue = QueueStore.load(this@MainActivity)
                    history = HistoryStore.load(this@MainActivity)
                }
                delay(800)
            }
        }
        GalleryShell(
            page = screen,
            onNavigate = { page ->
                keyboard?.hide()
                screen = page
            },
            onSettings = { openSettings() },
            onBack = ::backFromPage,
            actions = {
                if (screen == GalleryPage.DOWNLOADS && items.isNotEmpty()) {
                    DownloadActions(selected.size, searchAllowed && selected.isNotEmpty()) {
                        keyboard?.hide()
                        focus.clearFocus()
                        uploadStatus = ""
                        showDestination = true
                    }
                }
            }
        ) {
            when (screen) {
                GalleryPage.HOME -> GalleryPageColumn { GalleryHome(
                    url = url,
                    onUrlChange = ::setGalleryUrl,
                    searchAllowed = searchAllowed,
                    hasResults = items.isNotEmpty(),
                    onSearch = startSearch,
                    onResults = { screen = GalleryPage.DOWNLOADS },
                    mailConfigured = email.isNotBlank() && password.isNotBlank(),
                    driveConfigured = driveId.isNotBlank(),
                    onMailSettings = { openSettings("Mail WebDAV") },
                    onDriveSettings = { openSettings("Google Drive") },
                    latestTransfer = latestTransfer(),
                    onLatestTransfer = {
                        screen = if (savedQueue != null) GalleryPage.QUEUE else GalleryPage.HISTORY
                    }
                ) }
                GalleryPage.DOWNLOADS -> DownloadsPage(searchAllowed, startSearch)
                GalleryPage.QUEUE -> GalleryPageColumn { QueuePage() }
                GalleryPage.HISTORY -> GalleryPageColumn { HistoryPage() }
                GalleryPage.SETTINGS -> GalleryPageColumn { SettingsPage() }
            }
        }
    }

    private fun latestTransfer(): LatestTransfer? {
        val queue = savedQueue
        val last = history.firstOrNull()
        if (queue == null) {
            return last?.let {
                LatestTransfer(it.title.ifBlank { it.model }, it.target, it.uploaded + it.skipped, it.total, it.outcome)
            }
        }
        val completed = queue.completed
        val operation = when {
            transferRunning -> "Передача в фоне"
            last?.id == queue.id -> last.outcome
            completed == queue.entries.size -> "Завершено"
            else -> "Можно продолжить"
        }
        return LatestTransfer(queue.title.ifBlank { queue.model }, queue.target, completed, queue.entries.size, operation)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun DownloadsPage(searchAllowed: Boolean, startSearch: () -> Unit) {
        if (items.isEmpty()) {
            GalleryPageColumn {
                GalleryLinkField(url, ::setGalleryUrl, searchAllowed, startSearch)
                FindMediaButton(searchAllowed && url.isNotBlank(), startSearch)
                OutlinedButton(onClick = ::pickLocalMedia, enabled = searchAllowed,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Добавить видео или фото с устройства")
                }
                Text(status, style = MaterialTheme.typography.bodyMedium)
                if (scanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                QueueShortcut()
                if (GalleryKeys.canonical(url) != null) {
                    var metadataExpanded by rememberSaveable(url) { mutableStateOf(false) }
                    GalleryDisclosure("Данные публикации", metadataExpanded) { metadataExpanded = !metadataExpanded }
                    if (metadataExpanded) GalleryFieldsForm(searchAllowed)
                }
            }
            return
        }

        var linkExpanded by rememberSaveable(url) { mutableStateOf(false) }
        var metadataExpanded by rememberSaveable(url) { mutableStateOf(false) }
        var checksExpanded by rememberSaveable(url) { mutableStateOf(false) }
        val candidates = remember(items, showOtherMedia, recommendedUrls) {
            MediaResults.visible(items, showOtherMedia, recommendedUrls)
        }
        val photos = remember(candidates) { candidates.count { it.kind == Kind.PHOTO } }
        val otherLinks = items.size - remember(items, recommendedUrls) {
            MediaResults.visible(items, false, recommendedUrls).size
        }
        val videos = candidates.size - photos
        val visibleItems = remember(candidates, mediaFilter) {
            mediaFilter?.let { kind -> candidates.filter { it.kind == kind } } ?: candidates
        }
        val gridState = rememberLazyGridState()
        val fontScale = LocalDensity.current.fontScale
        val gridScope = rememberCoroutineScope()

        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (fontScale > 1.3f) 144.dp else 104.dp),
            state = gridState,
            modifier = Modifier.widthIn(max = GalleryContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Найдено файлов: ${candidates.size}", style = MaterialTheme.typography.titleLarge)
                    if (candidates.size != items.size) {
                        Text("Всего проанализировано ссылок: ${items.size}", style = MaterialTheme.typography.bodySmall)
                    }
                    MediaFilters(mediaFilter, photos, videos) { kind ->
                        mediaFilter = kind
                        gridScope.launch { gridState.scrollToItem(0) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                selected = selected + visibleItems
                                    .filter { videoProbeResults[it.url]?.blocked != true }
                                    .map(Media::url)
                            },
                            enabled = searchAllowed && visibleItems.any {
                                it.url !in selected && videoProbeResults[it.url]?.blocked != true
                            }
                        ) { Text("Выбрать все в фильтре") }
                        TextButton(onClick = { selected = emptySet() }, enabled = searchAllowed && selected.isNotEmpty()) {
                            Text("Снять выделение")
                        }
                    }
                    QueueShortcut()
                    OutlinedButton(
                        onClick = ::pickLocalMedia,
                        enabled = searchAllowed,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Добавить видео или фото с устройства") }
                    val totalVideoOptions = items.count {
                        it.kind == Kind.VIDEO && it.url.startsWith("https://")
                    }
                    val remainingUnchecked = VideoAvailabilityProbe.allCandidates(items)
                        .count { videoProbeResults[it.url]?.blocked != true }
                    if (totalVideoOptions > 0 || videoProbeReport.isNotBlank() || videoProbeRunning) {
                        OutlinedButton(
                            onClick = ::checkSelectedVideos,
                            enabled = searchAllowed && remainingUnchecked > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Проверить варианты видео ($totalVideoOptions)") }
                        if (videoProbeRunning) {
                            Text(videoProbeProgress, style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick = ::cancelVideoCheck) { Text("Остановить проверку") }
                        }
                        if (videoProbeReport.isNotBlank()) Text(videoProbeReport, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GalleryDisclosure("Данные публикации", metadataExpanded) { metadataExpanded = !metadataExpanded }
                    if (metadataExpanded) GalleryFieldsForm(searchAllowed)
                    GalleryDisclosure("Разрешение и варианты", checksExpanded) { checksExpanded = !checksExpanded }
                    if (checksExpanded) DownloadQualityControls(searchAllowed, otherLinks)
                }
            }
            items(visibleItems, key = { "media:${it.url}" }) { media ->
                val availability = videoProbeResults[media.url]
                val detail = if (media.kind == Kind.VIDEO) availability?.message ?: QualitySelector.hint(media)
                    else imageProbeResults[media.url]?.display()
                MediaTile(media, media.url in selected, searchAllowed && availability?.blocked != true, detail) { checked ->
                    selected = if (checked) selected + media.url else selected - media.url
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GalleryDisclosure("Поиск по ссылке", linkExpanded) { linkExpanded = !linkExpanded }
                    if (linkExpanded) {
                        GalleryLinkField(url, ::setGalleryUrl, searchAllowed, startSearch)
                        FindMediaButton(searchAllowed && url.isNotBlank(), startSearch)
                    }
                }
            }
        }
        if (showDestination) DestinationDialog()
    }

    @Composable
    private fun QueueShortcut() {
        if (transferRunning || (savedQueue?.remaining ?: 0) > 0) {
            TextButton(onClick = { screen = GalleryPage.QUEUE }) {
                Text(if (transferRunning) "Открыть ход загрузки" else "Открыть сохранённую очередь")
            }
        }
    }

    @Composable
    private fun GalleryFieldsForm(enabled: Boolean) {
        OutlinedTextField(model, { model = it }, label = { Text("Имя модели") },
            modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true)
        OutlinedTextField(title, { title = it }, label = { Text("Название публикации") },
            modifier = Modifier.fillMaxWidth(), enabled = enabled)
        OutlinedTextField(date, { date = it }, label = { Text("Дата ГГГГ-ММ-ДД") },
            modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            isError = !GalleryKeys.validDate(date.trim()),
            supportingText = if (GalleryKeys.validDate(date.trim())) null else { { Text("Укажите реальную дату") } })
        if (metadataReport.isNotBlank()) Text(metadataReport, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = ::saveGalleryMetadata,
            enabled = enabled && GalleryKeys.canonical(url) != null && model.isNotBlank() && title.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сохранить данные этой галереи") }
    }

    @Composable
    private fun DownloadQualityControls(searchAllowed: Boolean, others: Int) {
        if (others > 0) {
            TextButton(
                onClick = { showOtherMedia = !showOtherMedia; mediaFilter = null },
                enabled = searchAllowed
            ) { Text(if (showOtherMedia) "Скрыть дополнительные варианты" else "Показать ещё $others ссылок") }
        }
        OutlinedButton(
            onClick = { selected = recommendedUrls.filter {
                videoProbeResults[it]?.blocked != true
            }.toSet() }, enabled = searchAllowed && recommendedUrls.any {
                videoProbeResults[it]?.blocked != true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Выбрать рекомендуемые варианты") }
        OutlinedButton(
            onClick = ::verifyImageSizes,
            enabled = searchAllowed && ImageProbePlan.selectedPhotoCount(items, selected) > 0,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Проверить разрешение фото") }
        OutlinedButton(
            onClick = ::compareImageVariants, enabled = searchAllowed && selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сравнить варианты разрешения") }
        if (imageProbeRunning) {
            Text(imageProbeProgress, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            TextButton(onClick = ::cancelImageSizes) { Text("Остановить проверку") }
        } else if (imageProbeResults.isNotEmpty()) {
            Text("Размеры подтверждены у ${imageProbeResults.values.count { it.verified }} из ${imageProbeResults.size} проверенных файлов.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (comparisonReport.isNotBlank()) Text(comparisonReport, style = MaterialTheme.typography.bodySmall)
        if (imageProbeStatus.isNotBlank()) Text(imageProbeStatus, style = MaterialTheme.typography.bodySmall)
    }

    @Composable
    private fun DestinationDialog() {
        var showFields by rememberSaveable { mutableStateOf(model.isBlank() || title.isBlank() || !GalleryKeys.validDate(date.trim())) }
        val enabled = !working && !transferRunning
        AlertDialog(
            onDismissRequest = { if (enabled) showDestination = false },
            title = { Text("Куда загрузить") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DestinationPicker(target, enabled) { target = it; uploadStatus = "" }
                    val folder = when (target) {
                        "Mail WebDAV" -> mailRoot.ifBlank { "Корневая папка Mail" }
                        "Google Drive" -> if (driveId.isBlank()) "Папка не выбрана" else "Папка Google Drive задана"
                        else -> treeName
                    }
                    Text(folder, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { openSettings(target) }, enabled = enabled) {
                        Text("Настроить назначение")
                    }
                    GalleryDisclosure("Данные публикации", showFields) { showFields = !showFields }
                    if (showFields) GalleryFieldsForm(enabled)
                    if (uploadStatus.isNotBlank()) {
                        Text(uploadStatus, style = MaterialTheme.typography.bodyMedium,
                            color = if (working) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                    }
                    if (working) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = ::beginUpload, enabled = enabled && selected.isNotEmpty()) { Text("Начать загрузку") }
            },
            dismissButton = { TextButton(onClick = { showDestination = false }, enabled = enabled) { Text("Отмена") } }
        )
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun DestinationPicker(destination: String, enabled: Boolean = true, select: (String) -> Unit) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(destination == "Mail WebDAV", { select("Mail WebDAV") }, { Text("Mail WebDAV") }, enabled = enabled)
            FilterChip(destination == "Google Drive", { select("Google Drive") }, { Text("Google Drive API") }, enabled = enabled)
            FilterChip(destination == "Папка Android", { select("Папка Android") }, { Text("Папка Android / облака") }, enabled = enabled)
        }
    }

    @Composable
    private fun SettingsPage() {
        val settingsEnabled = !working && !transferRunning
        GalleryCard {
            Row(
                Modifier.fillMaxWidth().toggleable(value = dark, role = Role.Switch, onValueChange = ::setDarkTheme)
                    .padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Тёмная тема", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = dark, onCheckedChange = null)
            }
        }
        HorizontalDivider()
        Text("Хранилища", style = MaterialTheme.typography.titleMedium)
        DestinationPicker(settingsDestination) {
            settingsDestination = it
            reveal = false
        }
        when (settingsDestination) {
            "Mail WebDAV" -> {
                OutlinedTextField(
                    email, ::changeMailAccount, label = { Text("Почта Mail") },
                    enabled = settingsEnabled, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    password, { password = it }, label = { Text("Пароль внешнего приложения") },
                    enabled = settingsEnabled, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { reveal = !reveal }, enabled = settingsEnabled) {
                        Text(if (reveal) "Скрыть" else "Показать")
                    } }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberMailPassword, enabled = settingsEnabled, onCheckedChange = { checked ->
                        rememberMailPassword = checked
                        if (!checked) SecureMailCredentials.clear(this@MainActivity)
                    })
                    Text("Запомнить пароль на этом устройстве")
                }
                OutlinedTextField(
                    mailRoot, { mailRoot = it }, label = { Text("Путь в Облаке Mail") },
                    enabled = settingsEnabled, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    supportingText = { Text("Например, Images/Models") }
                )
            }
            "Google Drive" -> {
                OutlinedTextField(
                    driveId, { driveId = it }, label = { Text("ID папки Google Drive") },
                    enabled = settingsEnabled, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("Для доступа нужен OAuth-клиент с SHA-1 подписи установленной APK. Вход запрашивается при загрузке.",
                    style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                Text(treeName, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = { treePicker.launch(null) }, enabled = settingsEnabled, modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Выбрать папку в Android")
                }
            }
        }
        if (settingsDestination != "Папка Android") {
            Button(onClick = ::saveSettings, enabled = settingsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("Сохранить настройки")
            }
        }
        if (settingsStatus.isNotBlank()) Text(settingsStatus, style = MaterialTheme.typography.bodyMedium)
    }

    private fun changeMailAccount(newEmail: String) {
        if (newEmail == email) return
        email = newEmail
        val stored = SecureMailCredentials.load(this, newEmail)
        // Never reuse credentials entered for a different account, even when
        // the previous password was not saved in Android Keystore.
        password = stored.orEmpty()
        rememberMailPassword = stored != null
        reveal = false
    }

    private fun saveSettings() {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putString("email", email)
            .putString("driveId", driveId)
            .putString("mailRoot", mailRoot)
            .apply()
        settingsStatus = try {
            if (settingsDestination == "Mail WebDAV") {
                if (rememberMailPassword) SecureMailCredentials.save(this, email, password)
                else SecureMailCredentials.clear(this)
            }
            "Настройки сохранены"
        } catch (e: Exception) {
            "Настройки сохранены. Пароль Mail: ${e.message}"
        }
    }

    @Composable
    private fun TransferReport() {
        if (transferStatus.isNotBlank() && (transferRunning || savedQueue?.remaining != 0)) {
            Text(transferStatus, style = MaterialTheme.typography.bodySmall)
        }
        if (reportDetails.isBlank()) return
        TextButton(onClick = { showReportDetails = !showReportDetails }) {
            Text(if (showReportDetails) "Скрыть подробности" else "Показать подробности и причины ошибок")
        }
        if (showReportDetails) {
            GalleryCard {
                Text(reportDetails, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable private fun QueuePage() {
        val queue = savedQueue
        if (queue == null) {
            Text("Пока нет сохранённых заданий. Начните загрузку на главном экране.")
            return
        }
        if (showCloseQueueConfirmation) {
            AlertDialog(
                onDismissRequest = { showCloseQueueConfirmation = false },
                title = { Text("Закрыть незавершённую очередь?") },
                text = {
                    Text("Оставшиеся ${queue.remaining} файлов не будут загружены из этой очереди. " +
                        "Уже загруженные файлы останутся в хранилище. " +
                        "Итог задания сохранится в истории, после чего можно начать новую загрузку.")
                },
                confirmButton = {
                    TextButton(onClick = ::closeQueue) { Text("Закрыть очередь") }
                },
                dismissButton = {
                    TextButton(onClick = { showCloseQueueConfirmation = false }) { Text("Отмена") }
                }
            )
        }
        val done = queue.entries.count { it.state == "DONE" }
        val skipped = queue.entries.count { it.state == "SKIPPED" }
        val failed = queue.entries.count { it.state == "FAILED" }
        val completed = done + skipped
        val total = queue.entries.size
        GalleryCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(queue.model, style = MaterialTheme.typography.titleMedium)
                Text(queue.title, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                Text(queue.target, style = MaterialTheme.typography.bodySmall)
                Text("Готово: $completed из $total", style = MaterialTheme.typography.titleMedium)
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { completed.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                TransferCounts(done, skipped, failed, queue.remaining, queue.target)
                if (queue.target == "Папка Android" && done > 0) {
                    Text("Синхронизация зависит от выбранного провайдера папки.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (transferRunning) {
            Text("Передача выполняется в фоне")
            TextButton(onClick = { TransferService.cancel(this@MainActivity); transferStatus = "Остановка запрошена" }) { Text("Остановить передачу") }
        } else if (queue.remaining > 0) {
            if (queue.target == "Mail WebDAV") {
                OutlinedTextField(email, ::changeMailAccount, label={Text("Аккаунт Mail для продолжения")}, modifier=Modifier.fillMaxWidth())
                if (password.isBlank()) {
                    OutlinedTextField(password, {password=it}, label={Text("Пароль приложения Mail")}, visualTransformation=PasswordVisualTransformation(), modifier=Modifier.fillMaxWidth())
                }
            }
            Button(onClick=::resumeUpload, enabled = !working, modifier=Modifier.fillMaxWidth()) { Text("Продолжить оставшиеся ${queue.remaining} файлов") }
            OutlinedButton(
                onClick = { showCloseQueueConfirmation = true },
                enabled = !working && !auditing,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Закрыть очередь и начать новую") }
            Text(
                "Если источник запрещает скачивание (HTTP 403), проверьте разрешённый способ получения файла. " +
                    "Повтор без изменения доступа может снова завершиться ошибкой.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            if (queue.target == "Папка Android") {
                OutlinedButton(
                    onClick=::auditQueue,
                    enabled=!auditing,
                    modifier=Modifier.fillMaxWidth()
                ) { Text(if (auditing) "Проверка выполняется..." else "Проверить SHA-256 и размеры") }
                Text(
                    "Сверка повторно скачивает исходники и читает файлы из выбранной папки. До 32 МиБ на файл.",
                    style=MaterialTheme.typography.bodySmall
                )
                if (auditProgress.isNotBlank()) Text(auditProgress, style=MaterialTheme.typography.bodySmall)
                if (auditReport.isNotBlank()) Text(auditReport, style=MaterialTheme.typography.bodySmall)
            }
        }
        TransferReport()
        HorizontalDivider()
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(selected=queueFilter=="ALL", onClick={queueFilter="ALL"}, label={Text("Все")})
            FilterChip(selected=queueFilter=="PENDING", onClick={queueFilter="PENDING"}, label={Text("Осталось")})
            FilterChip(selected=queueFilter=="DONE", onClick={queueFilter="DONE"}, label={Text("Готово")})
            FilterChip(selected=queueFilter=="FAILED", onClick={queueFilter="FAILED"}, label={Text("Ошибки")})
        }
        val filtered = queue.entries.filter {
            when(queueFilter) {
                "PENDING" -> it.state == "PENDING" || it.state == "FAILED"
                "DONE" -> it.state == "DONE" || it.state == "SKIPPED"
                "FAILED" -> it.state == "FAILED"
                else -> true
            }
        }
        if (filtered.isEmpty()) Text("В этой категории файлов нет.")
        filtered.take(80).forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(when(item.state) {
                    "DONE" -> "✓"
                    "SKIPPED" -> "↷"
                    "FAILED" -> "!"
                    else -> "·"
                })
                Column(Modifier.weight(1f)) {
                    Text(item.media.name, style=MaterialTheme.typography.bodyMedium)
                    Text(when(item.state) {
                        "DONE" -> if (queue.target == "Папка Android") "Передан провайдеру" else "Загружен"
                        "SKIPPED" -> if (queue.target == "Папка Android") "Существует у провайдера" else "Уже был в облаке"
                        "FAILED" -> "Ошибка, можно повторить"
                        else -> "Ожидает"
                    }, style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (filtered.size > 80) Text("Показаны первые 80 из ${filtered.size} файлов.")
    }

    @Composable private fun HistoryPage() {
        if (history.isEmpty()) {
            Text("История пока пуста. Она появится после завершения или остановки передачи.")
            return
        }
        history.forEach { record ->
            GalleryCard {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(record.model, style=MaterialTheme.typography.titleMedium)
                    Text(record.title, maxLines=2, style=MaterialTheme.typography.bodyMedium)
                    Text("${record.target} · ${record.outcome}", style=MaterialTheme.typography.bodySmall)
                    Text("Готово: ${record.uploaded + record.skipped} из ${record.total}", style = MaterialTheme.typography.bodySmall)
                    TransferCounts(record.uploaded, record.skipped, record.failed, record.remaining, record.target)
                    Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(record.updatedAt)), style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @Composable
    private fun TransferCounts(uploaded: Int, skipped: Int, failed: Int, remaining: Int, destination: String) {
        val first = if (destination == "Папка Android") "Передано" else "Загружено"
        Text("$first: $uploaded · Уже были: $skipped", style = MaterialTheme.typography.bodySmall)
        Text("Ошибки: $failed · Осталось: $remaining", style = MaterialTheme.typography.bodySmall)
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
