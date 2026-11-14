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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.gallerybridge.app.core.*
import com.gallerybridge.app.network.Services
import com.gallerybridge.app.network.IntegrityAudit
import com.gallerybridge.app.network.ImageQualityProbe
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
    private var status by mutableStateOf("Вставьте HTTPS-ссылку на галерею или файл.")
    private var items by mutableStateOf<List<Media>>(emptyList())
    private var selected by mutableStateOf<Set<String>>(emptySet())
    private var reveal by mutableStateOf(false)
    private var rememberMailPassword by mutableStateOf(false)
    private var showOtherMedia by mutableStateOf(false)
    private var mediaPageLimit by mutableIntStateOf(MediaResults.PAGE_SIZE)
    private var recommendedUrls by mutableStateOf<Set<String>>(emptySet())
    private var imageProbeRunning by mutableStateOf(false)
    private var imageProbeProgress by mutableStateOf("")
    private var imageProbeResults by mutableStateOf<Map<String, ImageQualityProbe.Result>>(emptyMap())
    private var comparisonPairs by mutableStateOf<List<VariantComparison.Pairing>>(emptyList())
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
    private var auditing by mutableStateOf(false)
    private var auditProgress by mutableStateOf("")
    private var auditReport by mutableStateOf("")

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
        settingsOrigin = savedInstanceState.galleryPage("settingsOrigin")
        settingsDestination = savedInstanceState?.getString("settingsDestination") ?: target
        target = savedInstanceState?.getString("transferTarget") ?: target
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
            status = "Выбери фотографии для проверки разрешения"
            return
        }
        imageProbeJob = scope.launch {
            imageProbeRunning = true
            imageProbeResults = emptyMap()
            try {
                candidates.forEachIndexed { index, item ->
                    ensureActive()
                    imageProbeProgress = "Проверка разрешения ${index + 1} из ${candidates.size}: ${item.name}"
                    val result = ImageQualityProbe.inspect(item)
                    ensureActive()
                    imageProbeResults = imageProbeResults + (item.url to result)
                }
                val verified = imageProbeResults.values.count { it.verified }
                status = "Проверено разрешение $verified из ${candidates.size} изображений. " +
                    "Неудачные проверки помечены отдельно; остальные файлы не изменены."
            } catch (_: CancellationException) {
                status = "Проверка разрешения остановлена. Полученные результаты сохранены."
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
            comparisonPairs = emptyList()
            comparisonReport = "Для выбранных файлов не найдены другие варианты с тем же именем основы и в той же папке CDN."
            return
        }
        comparisonPairs = pairs
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

    private fun cancelImageSizes() {
        imageProbeJob?.cancel()
        ImageQualityProbe.cancel()
    }

    private fun setGalleryUrl(newUrl: String) {
        val changed = GalleryKeys.canonical(url) != GalleryKeys.canonical(newUrl) ||
            (newUrl.isBlank() && url.isNotBlank())
        url = newUrl
        if (!changed) return
        items = emptyList()
        selected = emptySet()
        recommendedUrls = emptySet()
        imageProbeResults = emptyMap()
        comparisonPairs = emptyList()
        comparisonReport = ""
        mediaPageLimit = MediaResults.PAGE_SIZE
        showOtherMedia = false
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
        status = "Найди фото и видео по этой ссылке."
    }

    private fun saveGalleryMetadata() {
        status = try {
            GalleryMetadataStore.save(this, url, GalleryFields(model, title, date))
            "Данные этой галереи сохранены на устройстве. Они будут восстановлены по той же ссылке."
        } catch (e: Exception) {
            "Не удалось сохранить данные галереи: ${e.message}"
        }
    }

    private fun scan() {
        if (working || transferRunning || imageProbeRunning || url.isBlank()) return
        val requestedUrl = url
        screen = GalleryPage.DOWNLOADS
        working = true
        status = "Проверяем ссылку…"
        items = emptyList()
        selected = emptySet()
        recommendedUrls = emptySet()
        imageProbeResults = emptyMap()
        comparisonPairs = emptyList()
        comparisonReport = ""
        metadataReport = ""
        mediaPageLimit = MediaResults.PAGE_SIZE
        showOtherMedia = false
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
                working = false
                }
        }
    }
    private fun upload(token: String?) {
        if (transferRunning) {
            working = false
            status = "Загрузка уже выполняется в фоне"
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
                this, chosen, model, title, date, target, email, password,
                if (target == "Google Drive") driveId else if (target == "Папка Android") treeUri else mailRoot, token
            )
            savedQueue = QueueStore.load(this)
            transferRunning = true
            screen = GalleryPage.QUEUE
            transferStatus = "Фоновая передача запущена"
        } catch (e: Exception) {
            status = "Не удалось запустить фоновую передачу: ${e.message}"
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
        if (transferRunning) { status = "Загрузка уже выполняется в фоне"; return }
        if (selected.isEmpty()) { status = "Нет выбранных файлов"; return }
        if (model.isBlank() || title.isBlank()) { status = "Укажите имя модели и название публикации перед загрузкой"; return }
        if (target == "Mail WebDAV") {
            if (email.isBlank() || password.isBlank()) { status = "Укажите почту и пароль приложения Mail"; return }
            upload(null); return
        }
        if (target == "Папка Android") {
            if (treeUri.isBlank()) { status = "Сначала выбери папку назначения"; return }
            upload(null); return
        }
        beginGoogleAuthorization()
    }

    private fun beginGoogleAuthorization() {
        working = true
        if (resumeRequest) transferStatus = "Запрашиваем доступ к Google Drive…"
        else status = "Запрашиваем доступ к Google Drive…"
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
                "Google Drive: OAuth-клиент приложения не зарегистрирован. Нужны Google Cloud Drive API, package com.gallerybridge.app и SHA-1 именно установленной APK."
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
        if (resumeRequest) transferStatus = message else status = message
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
        val searchAllowed = !working && !transferRunning && !imageProbeRunning
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
            onBack = ::backFromPage
        ) {
            when (screen) {
                GalleryPage.HOME -> GalleryHome(
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
                )
                GalleryPage.DOWNLOADS -> DownloadsPage(searchAllowed, startSearch)
                GalleryPage.QUEUE -> QueuePage()
                GalleryPage.HISTORY -> HistoryPage()
                GalleryPage.SETTINGS -> SettingsPage()
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

    @Composable
    private fun DownloadsPage(searchAllowed: Boolean, startSearch: () -> Unit) {
        GalleryLinkField(url, ::setGalleryUrl, searchAllowed, startSearch)
        FindMediaButton(searchAllowed && url.isNotBlank(), startSearch)
        Text(status, style = MaterialTheme.typography.bodyMedium)
        if (working && !transferRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (transferRunning || (savedQueue?.remaining ?: 0) > 0) {
            TextButton(onClick = { screen = GalleryPage.QUEUE }) {
                Text(if (transferRunning) "Открыть ход загрузки" else "Открыть сохранённую очередь")
            }
        }
        OutlinedTextField(model, {model=it}, label={Text("Имя модели")}, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(title, {title=it}, label={Text("Название публикации")}, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(date, {date=it}, label={Text("Дата YYYY-MM-DD")}, modifier=Modifier.fillMaxWidth())
        if (metadataReport.isNotBlank()) Text(metadataReport, style=MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick=::saveGalleryMetadata,
            enabled=!working && !transferRunning && !imageProbeRunning &&
                GalleryKeys.canonical(url) != null &&
                model.isNotBlank() && title.isNotBlank(),
            modifier=Modifier.fillMaxWidth()
        ) { Text("Сохранить данные этой галереи") }
        Text(
            "Данные привязываются к HTTPS-ссылке и хранятся только на этом устройстве. " +
                "Дата должна соответствовать YYYY-MM-DD или оставаться пустой.",
            style=MaterialTheme.typography.bodySmall
        )
        Text("Найдено: ${items.size}; выбрано: ${selected.size}")
        val visibleItems = remember(items, showOtherMedia, recommendedUrls) {
            MediaResults.visible(items, showOtherMedia, recommendedUrls)
        }
        val displayedItems = remember(visibleItems, mediaPageLimit) {
            MediaResults.page(visibleItems, mediaPageLimit)
        }
        if (items.isNotEmpty()) {
            Row {
                TextButton(onClick={selected=visibleItems.map { it.url }.toSet()}) { Text("Выбрать видимые") }
                TextButton(onClick={selected=emptySet()}) { Text("Снять выделение") }
            }
            if (visibleItems.size < items.size || showOtherMedia) {
                TextButton(onClick={showOtherMedia=!showOtherMedia; mediaPageLimit=MediaResults.PAGE_SIZE}) {
                    Text(if (showOtherMedia) "Скрыть остальные варианты" else "Показать остальные ${items.size-visibleItems.size} ссылок")
                }
            }
        }
        if (items.isNotEmpty()) {
            OutlinedButton(
                onClick={selected=recommendedUrls},
                modifier=Modifier.fillMaxWidth(),
                enabled=!working
            ) { Text("Выбрать лучшие найденные варианты") }
            Text(
                "Выбор основан на признаках в URL и HTML, а не на проверенном разрешении. " +
                    "Для спорных файлов можно изменить галочки вручную.",
                style=MaterialTheme.typography.bodySmall
            )
        }
        if (visibleItems.size > displayedItems.size) {
            Text("Показано ${displayedItems.size} из ${visibleItems.size} ссылок", style=MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick={mediaPageLimit = (mediaPageLimit + MediaResults.PAGE_SIZE).coerceAtMost(visibleItems.size)},
                modifier=Modifier.fillMaxWidth(),
                enabled=!working
            ) { Text("Показать ещё ${minOf(MediaResults.PAGE_SIZE, visibleItems.size-displayedItems.size)}") }
        }
        if (items.isNotEmpty()) {
            OutlinedButton(
                onClick=::verifyImageSizes,
                enabled=!imageProbeRunning && !working && !transferRunning &&
                    ImageProbePlan.selectedPhotoCount(items, selected)>0,
                modifier=Modifier.fillMaxWidth()
            ) { Text("Проверить разрешение выбранных фотографий") }
            Text(
                "Проверка читает ограниченную часть каждого исходного изображения (до 4 МиБ). " +
                    "Максимум 40 фотографий за запуск, без сохранения и загрузки в облако.",
                style=MaterialTheme.typography.bodySmall
            )
            if (imageProbeRunning) {
                Text(imageProbeProgress, style=MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                TextButton(onClick=::cancelImageSizes) { Text("Остановить проверку") }
            }
            OutlinedButton(
                onClick=::compareImageVariants,
                enabled=!imageProbeRunning && !working && !transferRunning &&
                    selected.isNotEmpty(),
                modifier=Modifier.fillMaxWidth()
            ) { Text("Сравнить с другими вариантами") }
            Text(
                "Сопоставляет до 20 пар по URL из этой страницы. Проверка не добавляет новые адреса " +
                    "и не меняет выбранные фотографии.",
                style=MaterialTheme.typography.bodySmall
            )
            if (imageProbeResults.isNotEmpty()) {
                Text("Размеры получены для ${imageProbeResults.values.count { it.verified }} из ${imageProbeResults.size} проверенных файлов.")
            }
            if (comparisonReport.isNotBlank()) {
                Text(comparisonReport, style=MaterialTheme.typography.bodySmall)
            }
        }
        displayedItems.forEach { item -> Row {
            Checkbox(item.url in selected, onCheckedChange={v -> selected=if(v) selected+item.url else selected-item.url}, enabled=!working)
            Column {
                Text(item.name)
                Text(item.source, style=MaterialTheme.typography.bodySmall)
                val verified = imageProbeResults[item.url]
                Text(verified?.display() ?: QualitySelector.hint(item), style=MaterialTheme.typography.bodySmall)
            }
        }}
        Text("Куда загрузить", style = MaterialTheme.typography.titleMedium)
        DestinationPicker(target) { target = it }
        if (target == "Папка Android") Text("Папка: $treeName", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { openSettings(target) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Настроить назначение")
        }
        Button(
            onClick = ::beginUpload,
            enabled = !working && !transferRunning && !imageProbeRunning && selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Скачать и загрузить") }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun DestinationPicker(destination: String, select: (String) -> Unit) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(destination == "Mail WebDAV", { select("Mail WebDAV") }, { Text("Mail WebDAV") })
            FilterChip(destination == "Google Drive", { select("Google Drive") }, { Text("Google Drive API") })
            FilterChip(destination == "Папка Android", { select("Папка Android") }, { Text("Папка Android / облака") })
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
        if (stored != null || rememberMailPassword) {
            password = stored.orEmpty()
            rememberMailPassword = stored != null
        }
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
        if (transferStatus.isNotBlank()) Text(transferStatus, style = MaterialTheme.typography.bodySmall)
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
        val done = queue.entries.count { it.state == "DONE" }
        val skipped = queue.entries.count { it.state == "SKIPPED" }
        val failed = queue.entries.count { it.state == "FAILED" }
        Text(queue.model, style=MaterialTheme.typography.titleLarge)
        Text(queue.title, maxLines=3, style=MaterialTheme.typography.bodyMedium)
        Text("Облако: ${queue.target} · Файлов: ${queue.entries.size}", style=MaterialTheme.typography.bodySmall)
        val inCloud = done + skipped
        val percent = if (queue.entries.isEmpty()) 0 else (inCloud * 100 / queue.entries.size)
        Text(
            if (queue.target == "Папка Android")
                "Передано провайдеру: $inCloud из ${queue.entries.size} ($percent%)"
            else "В облаке: $inCloud из ${queue.entries.size} ($percent%)",
            style=MaterialTheme.typography.titleMedium
        )
        if (queue.target == "Папка Android") {
            Text(
                "Передача Android-провайдеру не подтверждает завершение синхронизации Google Диска.",
                style=MaterialTheme.typography.bodySmall
            )
        }
        if (queue.entries.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { inCloud.toFloat() / queue.entries.size },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Text("Новых: $done · Уже были: $skipped · Ошибок: $failed · Осталось: ${queue.remaining}")
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
        } else {
            Text("Очередь завершена. Можно добавить следующую галерею.")
            if (queue.target == "Папка Android") {
                OutlinedButton(
                    onClick=::auditQueue,
                    enabled=!auditing,
                    modifier=Modifier.fillMaxWidth()
                ) { Text(if (auditing) "Проверка выполняется..." else "Проверить SHA-256 и размеры") }
                Text(
                    "Ручная проверка скачивает исходные файлы снова и читает сохранённые через Google Drive. " +
                    "Она расходует трафик; файлы не изменяет. До 32 МиБ на файл.",
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
                    Text("${if (record.target == "Папка Android") "Передано провайдеру" else "В облаке"} ${record.uploaded + record.skipped}/${record.total} · Новых ${record.uploaded} · Уже было ${record.skipped}", style=MaterialTheme.typography.bodySmall)
                    Text("Ошибок ${record.failed} · Осталось ${record.remaining}", style=MaterialTheme.typography.bodySmall)
                    Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(record.updatedAt)), style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
