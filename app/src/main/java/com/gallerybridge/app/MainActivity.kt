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
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.gallerybridge.app.network.ModelListingScanWorker
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.viewModelScope
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.gallerybridge.app.core.*
import com.gallerybridge.app.network.Services
import com.gallerybridge.app.network.SafFolderAccess
import com.gallerybridge.app.network.IntegrityAudit
import com.gallerybridge.app.network.ImageQualityProbe
import com.gallerybridge.app.transfer.TransferService
import com.gallerybridge.app.transfer.QueueStore
import com.gallerybridge.app.transfer.HistoryStore
import com.gallerybridge.app.transfer.PortableHistoryBackup
import com.gallerybridge.app.transfer.PhotoOutcomeLedger
import com.gallerybridge.app.security.SecureMailCredentials
import com.gallerybridge.app.ui.*
import com.gallerybridge.app.update.UpdateManager
import com.gallerybridge.app.update.UpdateSettings
import androidx.compose.ui.Alignment
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private val session: GallerySessionState by viewModels()
    private val scope get() = session.viewModelScope
    private var url
        get() = session.url
        set(value) { session.url = value }
    private var model
        get() = session.model
        set(value) { session.model = value }
    private var title
        get() = session.title
        set(value) { session.title = value }
    private var date
        get() = session.date
        set(value) { session.date = value }
    private var email
        get() = session.email
        set(value) { session.email = value }
    private var password
        get() = session.password
        set(value) { session.password = value }
    private var treeUri
        get() = session.treeUri
        set(value) { session.treeUri = value }
    private var treeName
        get() = session.treeName
        set(value) { session.treeName = value }
    private var folderPermissionReady
        get() = session.folderPermissionReady
        set(value) { session.folderPermissionReady = value }
    private var folderPermissionMessage
        get() = session.folderPermissionMessage
        set(value) { session.folderPermissionMessage = value }
    private var mailRoot
        get() = session.mailRoot
        set(value) { session.mailRoot = value }
    private var target
        get() = session.target
        set(value) { session.target = value }
    private var dark
        get() = session.dark
        set(value) { session.dark = value }

    private var working
        get() = session.working
        set(value) { session.working = value }
    private var scanning
        get() = session.scanning
        set(value) { session.scanning = value }
    private var scanJob: Job?
        get() = session.scanJob
        set(value) { session.scanJob = value }
    private var modelListing
        get() = session.modelListing
        set(value) { session.modelListing = value }
    private var modelListingSelected
        get() = session.modelListingSelected
        set(value) { session.modelListingSelected = value }

    private var status
        get() = session.status
        set(value) { session.status = value }
    private var items
        get() = session.items
        set(value) { session.items = value }
    private var selected
        get() = session.selected
        set(value) { session.selected = value }
    private var reveal
        get() = session.reveal
        set(value) { session.reveal = value }
    private var rememberMailPassword
        get() = session.rememberMailPassword
        set(value) { session.rememberMailPassword = value }
    private var showOtherMedia
        get() = session.showOtherMedia
        set(value) { session.showOtherMedia = value }
    private var mediaFilter
        get() = session.mediaFilter
        set(value) { session.mediaFilter = value }
    private var showDestination
        get() = session.showDestination
        set(value) { session.showDestination = value }
    private var uploadStatus
        get() = session.uploadStatus
        set(value) { session.uploadStatus = value }
    private var recommendedUrls
        get() = session.recommendedUrls
        set(value) { session.recommendedUrls = value }
    private var imageProbeRunning
        get() = session.imageProbeRunning
        set(value) { session.imageProbeRunning = value }
    private var imageProbeProgress
        get() = session.imageProbeProgress
        set(value) { session.imageProbeProgress = value }
    private var imageProbeStatus
        get() = session.imageProbeStatus
        set(value) { session.imageProbeStatus = value }
    private var imageProbeResults
        get() = session.imageProbeResults
        set(value) { session.imageProbeResults = value }
    private var comparisonReport
        get() = session.comparisonReport
        set(value) { session.comparisonReport = value }
    private var metadataReport
        get() = session.metadataReport
        set(value) { session.metadataReport = value }
    private var suggestedPublicationDate
        get() = session.suggestedPublicationDate
        set(value) { session.suggestedPublicationDate = value }
    private var suggestedPublicationDateSource
        get() = session.suggestedPublicationDateSource
        set(value) { session.suggestedPublicationDateSource = value }

    private var imageProbeJob: Job?
        get() = session.imageProbeJob
        set(value) { session.imageProbeJob = value }
    private var reportDetails
        get() = session.reportDetails
        set(value) { session.reportDetails = value }
    private var showReportDetails
        get() = session.showReportDetails
        set(value) { session.showReportDetails = value }
    private var transferRunning
        get() = session.transferRunning
        set(value) { session.transferRunning = value }
    private var resumeRequest
        get() = session.resumeRequest
        set(value) { session.resumeRequest = value }
    private var savedQueue
        get() = session.savedQueue
        set(value) { session.savedQueue = value }
    private var history
        get() = session.history
        set(value) { session.history = value }
    private var catalog
        get() = session.catalog
        set(value) { session.catalog = value }
    private var catalogBackupStatus
        get() = session.catalogBackupStatus
        set(value) { session.catalogBackupStatus = value }
    // Ephemeral batch-review navigation; never writes into the durable transfer queue.
    private var batchReviewUrls
        get() = session.batchReviewUrls
        set(value) { session.batchReviewUrls = value }
    private var batchReviewIndex
        get() = session.batchReviewIndex
        set(value) { session.batchReviewIndex = value }
    private var pendingCatalogImport
        get() = session.pendingCatalogImport
        set(value) { session.pendingCatalogImport = value }
    private var screen
        get() = session.screen
        set(value) { session.screen = value }
    private var settingsOrigin
        get() = session.settingsOrigin
        set(value) { session.settingsOrigin = value }
    private var settingsDestination
        get() = session.settingsDestination
        set(value) { session.settingsDestination = value }
    private var settingsStatus
        get() = session.settingsStatus
        set(value) { session.settingsStatus = value }
    private var transferStatus
        get() = session.transferStatus
        set(value) { session.transferStatus = value }
    private var queueFilter
        get() = session.queueFilter
        set(value) { session.queueFilter = value }
    private var showCloseQueueConfirmation
        get() = session.showCloseQueueConfirmation
        set(value) { session.showCloseQueueConfirmation = value }
    private var showRepeatConfirmation
        get() = session.showRepeatConfirmation
        set(value) { session.showRepeatConfirmation = value }
    // Consent is scoped to the exact gallery, destination and selected photo URLs.
    private data class RepeatReviewKey(
        val source: String, val target: String, val destination: String,
        val account: String, val photos: List<String>
    )
    private var repeatConsent: RepeatReviewKey? = null

    private fun repeatReviewKey() = RepeatReviewKey(
        GalleryKeys.canonical(url).orEmpty(), target,
        if (target == "Папка Android") treeUri else mailRoot,
        if (target == "Mail WebDAV") email.trim() else "",
        selected.sorted()
    )
    private var pendingUnblock
        get() = session.pendingUnblock
        set(value) { session.pendingUnblock = value }
    private var auditing
        get() = session.auditing
        set(value) { session.auditing = value }
    private var auditProgress
        get() = session.auditProgress
        set(value) { session.auditProgress = value }
    private var auditReport
        get() = session.auditReport
        set(value) { session.auditReport = value }
    private val updater get() = session.updater

    private val mediaPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        if (working || transferRunning || imageProbeRunning) {
            status = "Дождитесь завершения текущей операции."
            return@registerForActivityResult
        }
        val imported = mutableListOf<Media>()
        var unsupported = 0
        for (uri in uris.take(LocalMediaImport.MAX_FILES)) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val media = LocalMediaImport.fromDocument(this, uri)
                if (media?.kind == Kind.PHOTO) imported.add(media) else unsupported++
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
        if (!working && !transferRunning && !imageProbeRunning) {
            mediaPicker.launch(arrayOf("image/*"))
        }
    }

    private val treePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val newUri = uri.toString()
                val access = SafFolderAccess.inspect(this, newUri)
                require(access.ready) { access.message }
                // Persist first. A failed replacement never erases the previous folder.
                check(getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putString("treeUri", newUri).commit()) { "Не удалось сохранить выбор папки" }
                treeUri = newUri
                refreshFolderAccess()
                settingsStatus = "Выбрана папка: $treeName"
            } catch (e: Exception) {
                refreshFolderAccess()
                settingsStatus = "Не удалось выбрать новую папку: ${e.message}"
            }
        }
    }

    private val catalogExportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            catalogBackupStatus = try {
                val current = GalleryCatalogStore.load(this)
                val previousHistory = HistoryStore.load(this)
                val body = GalleryPortableBackup.export(current, previousHistory, dark)
                contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(body)
                    it.flush()
                } ?: throw IOException("Не удалось открыть файл для записи")
                "Сохранено галерей: ${current.size}; записей истории: ${previousHistory.size}."
            } catch (e: Exception) {
                "Не удалось экспортировать каталог: ${e.message}"
            }
        }
    }

    private val catalogImportPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            catalogBackupStatus = try {
                val stream = contentResolver.openInputStream(uri)
                    ?: throw IOException("Не удалось прочитать файл")
                val restored = stream.use {
                    GalleryPortableBackup.parse(GalleryCatalogBackup.readLimited(it))
                }
                val galleries = GalleryCatalogBackup.plan(GalleryCatalogStore.load(this), restored.galleries)
                val transfers = PortableHistoryBackup.plan(HistoryStore.load(this), restored.history)
                if (galleries.added == 0 && transfers.added == 0 &&
                    (restored.darkTheme == null || restored.darkTheme == dark)) {
                    "Новых данных нет. Галерей уже есть: ${galleries.alreadyPresent}; " +
                        "записей истории: ${transfers.alreadyPresent}."
                } else {
                    pendingCatalogImport = restored
                    "Новых галерей: ${galleries.added}; записей истории: ${transfers.added}."
                }
            } catch (e: Exception) {
                pendingCatalogImport = null
                "Ошибка импорта: ${e.message}"
            }
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Notification permission is optional for running a dataSync foreground service.
        if (resumeRequest) beginResumeTransfer() else beginServiceTransfer()
        resumeRequest = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!session.initialized) {
        val p = getSharedPreferences("settings", MODE_PRIVATE)
        model = p.getString("model", "") ?: ""
        email = p.getString("email", "") ?: ""
        SecureMailCredentials.load(this, email)?.let {
            password = it
            rememberMailPassword = true
        }
        treeUri = p.getString("treeUri", "") ?: ""
        refreshFolderAccess()
        mailRoot = p.getString("mailRoot", "Models") ?: "Models"
        dark = p.getBoolean("dark", true)
        applySystemBars()
        // Legacy Google Drive API was removed. Never turn an old API queue into a SAF task.
        target = TransferTargets.restore(savedInstanceState?.getString("transferTarget")
            ?: p.getString("transferTarget", target))
        settingsOrigin = savedInstanceState.galleryPage("settingsOrigin")
        settingsDestination = TransferTargets.restore(savedInstanceState?.getString("settingsDestination") ?: target)
        screen = savedInstanceState.galleryPage("galleryPage")
        savedQueue = QueueStore.load(this)
        history = HistoryStore.load(this)
        catalog = GalleryCatalogStore.load(this)
        batchReviewUrls = GalleryBatchSelection.plan(
            catalog, savedInstanceState?.getStringArrayList("batchReviewUrls") ?: emptyList(),
            savedQueue
        )
        batchReviewIndex = savedInstanceState?.getInt("batchReviewIndex", 0)
            ?.coerceIn(0, (batchReviewUrls.size - 1).coerceAtLeast(0)) ?: 0
        transferRunning = getSharedPreferences(TransferService.PREFS, MODE_PRIVATE)
            .getBoolean(TransferService.KEY_RUNNING, false)
        val previousLink = savedInstanceState?.getString("galleryUrl")
        val sharedLink = intent?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.startsWith("https://") }
        val previousSession = GallerySearchSessionStore.load(this)
        (previousLink ?: sharedLink ?: previousSession?.url)?.let(::setGalleryUrl)
        if (sharedLink == null && previousSession != null &&
            (url == previousSession.url || GalleryKeys.same(url, previousSession.url))) {
            model = previousSession.model
            title = previousSession.title
            date = previousSession.date
            items = previousSession.items
            selected = previousSession.selected
            recommendedUrls = previousSession.recommended
            if (savedInstanceState == null) {
                screen = GalleryPage.entries.firstOrNull { it.name == previousSession.screen }
                    ?: GalleryPage.HOME
            }
            if (ModelListingSources.accepts(url)) {
                ModelListingCheckpointStore.read(this)?.takeIf { it.source == url && it.finished }
                    ?.let { modelListing = it.result() }
            }
        }
        if (previousLink != null) {
            model = savedInstanceState.getString("galleryModel") ?: model
            title = savedInstanceState.getString("galleryTitle") ?: title
            date = savedInstanceState.getString("galleryDate") ?: date
        }
        session.initialized = true
        }
        // Refresh durable state even when ViewModel survives Activity recreation.
        // A foreground transfer or Android component may have changed these stores.
        savedQueue = QueueStore.load(this)
        history = HistoryStore.load(this)
        catalog = GalleryCatalogStore.load(this)
        transferRunning = getSharedPreferences(TransferService.PREFS, MODE_PRIVATE)
            .getBoolean(TransferService.KEY_RUNNING, false)
        applySystemBars()
        setContent { GalleryTheme(dark) { Ui() } }
        observeModelScan()
        if (packageName == "com.gallerybridge.app.preview.signed") updater.check(auto = true)
    }

    override fun onResume() {
        super.onResume()
        refreshFolderAccess()
    }

    /** On backgrounding, preserve viewing results without mutating the transfer queue. */
    override fun onStop() {
        if (session.initialized) saveViewingSession()
        super.onStop()
    }

    private fun saveViewingSession() {
        runCatching {
            GallerySearchSessionStore.save(this, GallerySearchSessionStore.Snapshot(
                url, model, title, date, screen.name, items, selected, recommendedUrls))
        }
    }

    /** WorkManager owns the network task; UI observes it after Activity recreation. */
    private fun observeModelScan() {
        lifecycleScope.launch {
            WorkManager.getInstance(applicationContext)
                .getWorkInfosForUniqueWorkFlow(ModelListingScanWorker.UNIQUE)
                .collect { infos ->
                    val checkpoint = ModelListingCheckpointStore.read(this@MainActivity)
                        ?: return@collect
                    if (url != checkpoint.source) return@collect
                    val info = infos.firstOrNull { it.id.toString() == checkpoint.requestId }
                        ?: return@collect
                    when (info.state) {
                        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                            scanning = true
                            working = true
                            val n = info.progress.getInt(ModelListingScanWorker.LOADED,
                                checkpoint.pages.size)
                            val total = info.progress.getInt(ModelListingScanWorker.TOTAL, 0)
                            status = if (total > 0) "Чтение страниц каталога: $n из $total."
                                else "Поиск публикаций продолжается в фоне. Страниц: $n."
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            scanning = false
                            working = false
                            if (checkpoint.finished) {
                                modelListing = checkpoint.result()
                                status = "Найдено фотопубликаций: ${modelListing?.photos?.size ?: 0}. " +
                                    if (modelListing?.incomplete == true)
                                        "Список может быть неполным, проверьте сообщение ниже."
                                    else "Обработаны доступные страницы."
                            } else status = "Не удалось сохранить результат поиска."
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            scanning = false
                            working = false
                            if (checkpoint.pages.isNotEmpty()) {
                                modelListing = checkpoint.result()
                                status = "Поиск остановлен. Показаны только подтверждённые " +
                                    "страницы (${checkpoint.pages.size}); список неполный."
                            } else {
                                status = if (info.state == WorkInfo.State.FAILED)
                                    "Поиск не выполнен. Проверьте сеть и повторите."
                                    else "Поиск отменён."
                            }
                        }
                    }
                }
        }
    }

    private fun stopSearch() {
        if (ModelListingSources.accepts(url) && scanning) {
            WorkManager.getInstance(applicationContext)
                .cancelUniqueWork(ModelListingScanWorker.UNIQUE)
            status = "Поиск остановлен. Ранее найденные публикации сохраняются."
        } else {
            scanJob?.cancel()
            status = "Поиск прерывается; передача не запускалась."
        }
    }

    private fun refreshFolderAccess() {
        val current = SafFolderAccess.inspect(this, treeUri)
        folderPermissionReady = current.ready
        treeName = current.name
        folderPermissionMessage = current.message
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
        outState.putStringArrayList("batchReviewUrls", ArrayList(batchReviewUrls))
        outState.putInt("batchReviewIndex", batchReviewIndex)
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

    private fun cancelImageSizes() {
        imageProbeJob?.cancel()
        ImageQualityProbe.cancel()
    }

    private fun batchOpenCurrent() {
        val selectedUrl = batchReviewUrls.getOrNull(batchReviewIndex) ?: return
        val fresh = GalleryBatchSelection.plan(
            GalleryCatalogStore.load(this), listOf(selectedUrl), QueueStore.load(this)
        )
        if (fresh.isEmpty()) {
            batchReviewUrls = emptyList()
            status = "Пакетный просмотр изменился: запись больше недоступна."
            screen = GalleryPage.CATALOG
            return
        }
        setGalleryUrl(selectedUrl)
        screen = GalleryPage.HOME
    }

    private fun batchBegin(urls: List<String>) {
        batchReviewUrls = GalleryBatchSelection.plan(
            GalleryCatalogStore.load(this), urls, QueueStore.load(this)
        )
        batchReviewIndex = 0
        if (batchReviewUrls.isNotEmpty()) batchOpenCurrent()
    }

    private fun batchNext() {
        if (working || scanning || transferRunning || imageProbeRunning ||
            getSharedPreferences(TransferService.PREFS, MODE_PRIVATE)
                .getBoolean(TransferService.KEY_RUNNING, false) ||
            (QueueStore.load(this)?.remaining ?: 0) > 0
        ) {
            status = "Остановите передачу или завершите сохранённую очередь перед переходом."
            return
        }
        if (batchReviewIndex + 1 >= batchReviewUrls.size) {
            batchReviewUrls = emptyList()
            catalog = GalleryCatalogStore.load(this)
            screen = GalleryPage.CATALOG
            return
        }
        batchReviewIndex += 1
        batchOpenCurrent()
    }

    private fun batchClose() {
        batchReviewUrls = emptyList()
        catalog = GalleryCatalogStore.load(this)
        screen = GalleryPage.CATALOG
    }

    private fun setGalleryUrl(newUrl: String) {
        val changed = GalleryKeys.canonical(url) != GalleryKeys.canonical(newUrl) ||
            (newUrl.isBlank() && url.isNotBlank())
        url = newUrl
        if (!changed) return
        cancelImageSizes()
        items = emptyList()
        selected = emptySet()
        modelListing = null
        modelListingSelected = emptySet()
        recommendedUrls = emptySet()
        imageProbeResults = emptyMap()
        imageProbeStatus = ""
        comparisonReport = ""
        showOtherMedia = false
        mediaFilter = null
        showDestination = false
        showRepeatConfirmation = false
        repeatConsent = null
        uploadStatus = ""
        model = ""
        title = ""
        date = ""
        metadataReport = ""
        suggestedPublicationDate = ""
        suggestedPublicationDateSource = ""
        val cached = GalleryMetadataStore.load(this, newUrl)
        if (cached != null) {
            model = cached.model
            title = cached.title
            date = cached.date
            metadataReport = "Восстановлены сохранённые данные для этой галереи."
        } else {
            // Imported galleries are portable without copying private app preferences.
            catalog.firstOrNull { GalleryKeys.same(it.url, newUrl) }?.let { entry ->
                model = entry.model
                title = entry.title
                date = entry.date
                metadataReport = "Данные восстановлены из каталога галерей."
            }
        }
        status = if (newUrl.isBlank()) "Вставьте HTTPS-ссылку на фотогалерею." else "Запустите поиск по ссылке."
    }

    /** Explicit import only; existing labels, history, queue and credentials remain intact. */
    private fun saveListingSelection() {
        val discovered = modelListing ?: return
        val chosen = discovered.photos.filter { it.url in modelListingSelected }.take(100)
        val current = GalleryCatalogStore.load(this)
        val additions = chosen.filterNot { GalleryCatalogStore.contains(current, it.url) }
            .map { pub ->
                CatalogGallery(pub.url, pub.model.ifBlank { "Без имени" },
                    pub.title, pub.date, System.currentTimeMillis())
            }
        val plan = try {
            GalleryCatalogStore.importMissing(this, additions)
        } catch (e: Exception) {
            status = "Не удалось сохранить публикации: ${e.message}"
            return
        }
        catalog = GalleryCatalogStore.load(this)
        modelListingSelected = emptySet()
        status = "Сохранено новых публикаций: ${plan.added}; " +
            "не поместилось: ${plan.noSpace}. Передачи и файлы не затронуты."
    }

    private fun openListingPublication(pub: ModelListingSources.Publication) {
        setGalleryUrl(pub.url)
        if (model.isBlank()) model = pub.model
        if (title.isBlank()) title = pub.title
        if (date.isBlank()) date = pub.date
        screen = GalleryPage.HOME
    }

    private fun saveGalleryMetadata() {
        metadataReport = try {
            val fields = GalleryFields(model, title, date)
            GalleryMetadataStore.save(this, url, fields)
            check(GalleryCatalogStore.remember(this, url, fields)) { "Не удалось добавить запись в каталог" }
            catalog = GalleryCatalogStore.load(this)
            "Данные сохранены в каталоге для этой ссылки."
        } catch (e: Exception) {
            "Не удалось сохранить данные галереи: ${e.message}"
        }
    }

    private fun scan() {
        if (working || transferRunning || imageProbeRunning || url.isBlank()) return
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
        comparisonReport = ""
        metadataReport = ""
        suggestedPublicationDate = ""
        suggestedPublicationDateSource = ""
        showOtherMedia = false
        mediaFilter = null
        showDestination = false
        uploadStatus = ""
        scanJob?.cancel()
        if (ModelListingSources.accepts(requestedUrl)) {
            try {
                ModelListingScanWorker.enqueue(applicationContext, requestedUrl)
                modelListing = null
                modelListingSelected = emptySet()
                status = "Поиск фотопубликаций запущен. Можно свернуть приложение."
            } catch (e: Exception) {
                scanning = false
                working = false
                status = "Не удалось запустить поиск: ${e.message ?: "ошибка"}"
            }
            return
        }
        scanJob = scope.launch {
            try {
                val scan = Services.scan(requestedUrl)
                val photos = scan.media.filter { it.kind == Kind.PHOTO }
                val recommended = withContext(Dispatchers.Default) {
                    QualitySelector.recommended(photos)
                }
                if (requestedUrl != url) return@launch
                val saved = GalleryMetadataStore.load(this@MainActivity, requestedUrl)
                // Explicit saved edits always win; visible H1 wins over SEO title
                // for new galleries. Never overwrite confirmed or user-entered dates.
                if (title.isBlank()) title = saved?.title?.takeIf { it.isNotBlank() } ?: scan.title
                if (date.isBlank()) date = saved?.date?.takeIf { it.isNotBlank() } ?: scan.date
                if (model.isBlank()) model = saved?.model?.takeIf { it.isNotBlank() } ?: scan.suggestedModel
                if (date.isBlank() && scan.suggestedDate.isNotBlank()) {
                    suggestedPublicationDate = scan.suggestedDate
                    suggestedPublicationDateSource = scan.suggestedDateSource
                }
                metadataReport = listOfNotNull(
                    "Сохранённые данные восстановлены.".takeIf { saved != null },
                    "Проверьте имя модели, предложенное по заголовку страницы.".takeIf { scan.suggestedModel.isNotBlank() },
                    "Дата публикации подтверждена: ${scan.dateSource}.".takeIf { scan.date.isNotBlank() && date == scan.date },
                    "Возможная дата обнаружена по пути изображений, требуется подтверждение.".takeIf { suggestedPublicationDate.isNotBlank() },
                    "Точная дата публикации не найдена.".takeIf { date.isBlank() && suggestedPublicationDate.isBlank() }
                ).joinToString(" ")
                recommendedUrls = recommended
                selected = recommended
                items = photos
                saveViewingSession()
                status = if (items.isEmpty()) "Фотографии по этой ссылке не найдены."
                    else "Найдено фотографий: ${items.size}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestedUrl == url) status = "Ошибка поиска: ${e.message}"
            } finally {
                scanning = false
                working = false
                scanJob = null
            }
        }
    }
    private fun upload() {
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
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            beginServiceTransfer()
        }
    }

    private fun beginServiceTransfer() {
        try {
            if (target == "Mail WebDAV" && rememberMailPassword && password.isNotBlank()) {
                SecureMailCredentials.save(this, email, password)
            }
            val chosen = items.filter { it.kind == Kind.PHOTO && it.url in selected }
            require(chosen.isNotEmpty()) { "Выберите фотографии для передачи" }
            if (target == "Папка Android") {
                val access = SafFolderAccess.inspect(this, treeUri)
                folderPermissionReady = access.ready
                treeName = access.name
                folderPermissionMessage = access.message
                require(access.ready) { access.message }
            }
            TransferService.begin(
                this, chosen, model, title, date.trim(), target, email, password,
                if (target == "Папка Android") treeUri else mailRoot,
                GalleryKeys.canonical(url)?.takeIf { chosen.all { media -> media.url.startsWith("https://") } }.orEmpty(),
                allowRepeatedGallery = repeatConsent == repeatReviewKey()
            )
            savedQueue = QueueStore.load(this)
            savedQueue?.sourceUrl?.takeIf { it.isNotBlank() }?.let { source ->
                runCatching { GalleryCatalogStore.remember(this, source, GalleryFields(model, title, date)) }
            }
            catalog = GalleryCatalogStore.load(this)
            getSharedPreferences("settings", MODE_PRIVATE).edit().putString("transferTarget", target).apply()
            transferRunning = true
            showDestination = false
            screen = GalleryPage.QUEUE
            transferStatus = "Фоновая передача запущена"
        } catch (e: Exception) {
            uploadStatus = "Не удалось запустить фоновую передачу: ${e.message}"
        } finally {
            repeatConsent = null
            showRepeatConfirmation = false
            working = false
        }
    }
    private fun beginResumeTransfer() {
        try {
            val queue = QueueStore.load(this) ?: error("Очередь не найдена")
            require(queue.entries.filter { it.state != "DONE" && it.state != "SKIPPED" }
                .all { it.media.kind == Kind.PHOTO }) {
                "Очередь содержит видео из прежней версии. Закройте её, чтобы начать новую фотозагрузку."
            }
            if (queue.target == "Папка Android") {
                val access = SafFolderAccess.inspect(this, queue.root)
                require(access.ready) {
                    "Исходная папка сохранённой очереди недоступна. ${access.message} " +
                        "Выберите ту же папку и повторите продолжение."
                }
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
            require(TransferTargets.isSupported(queue.target)) {
                "Очередь Google Drive API из прежней версии больше не поддерживается. " +
                    "Закройте её с сохранением истории и создайте новую загрузку через папку Android."
            }
            TransferService.resume(this, email, password)
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
            catalog = GalleryCatalogStore.load(this)
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
        if (queue.entries.any { it.state != "DONE" && it.state != "SKIPPED" && it.media.kind != Kind.PHOTO }) {
            transferStatus = "Это сохранённое задание содержит видео из прежней версии. Закройте очередь и начните новую с фотографиями."
            return
        }
        if (!TransferTargets.isSupported(queue.target)) {
            transferStatus = "Эта очередь создана для удалённого Google Drive API. " +
                "Закройте её с сохранением истории и начните новую передачу через папку Android."
            return
        }
        uploadResume()
    }

    private fun uploadResume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            resumeRequest = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else beginResumeTransfer()
    }

    private fun beginUpload() {
        if (working || transferRunning || imageProbeRunning || selected.isEmpty()) return
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
            "Папка Android" -> {
                val access = SafFolderAccess.inspect(this, treeUri)
                folderPermissionReady = access.ready
                treeName = access.name
                folderPermissionMessage = access.message
                access.message.takeUnless { access.ready }
            }
            else -> "Этот способ передачи больше не поддерживается."
        }
        if (setupError != null) {
            uploadStatus = setupError
            return
        }
        val selectedPhotos = items.filter { it.kind == Kind.PHOTO && it.url in selected }
        val matched = try {
            PhotoOutcomeLedger.matchedUrls(
                this, url, target, if (target == "Папка Android") treeUri else mailRoot,
                if (target == "Mail WebDAV") email.trim() else "",
                selectedPhotos, QueueStore.load(this)
            )
        } catch (e: Exception) {
            uploadStatus = "Журнал фотографий недоступен: ${e.message}. Новая передача заблокирована."
            return
        }
        when (GalleryTransferGuard.assess(
            url, GalleryCatalogStore.load(this), QueueStore.load(this), matched.isNotEmpty()
        )) {
            GalleryTransferGuard.Decision.BLOCKED -> {
                uploadStatus = "У этой публикации осталась незавершённая очередь. Откройте её и проверьте файлы перед повтором."
                return
            }
            GalleryTransferGuard.Decision.REVIEW -> {
                if (repeatConsent != repeatReviewKey()) {
                    showRepeatConfirmation = true
                    return
                }
            }
            GalleryTransferGuard.Decision.CLEAR -> repeatConsent = null
        }
        uploadStatus = ""
        working = true
        upload()
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
        // Persist edits and photo selection before Android may reclaim the process.
        // No password, authentication token, cloud queue or user file body is persisted.
        LaunchedEffect(url, model, title, date, screen, items, selected, recommendedUrls) {
            if (url.isBlank() || GalleryKeys.canonical(url) != null) {
                delay(350)
                withContext(Dispatchers.IO) {
                    saveViewingSession()
                }
            }
        }
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
                    catalog = GalleryCatalogStore.load(this@MainActivity)
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
                GalleryPage.HOME -> GalleryPageColumn {
                    BatchReviewControls()
                    GalleryHome(
                    url = url,
                    onUrlChange = ::setGalleryUrl,
                    searchAllowed = searchAllowed,
                    hasResults = items.isNotEmpty(),
                    onSearch = startSearch,
                    onResults = { screen = GalleryPage.DOWNLOADS },
                    catalogCount = catalog.size,
                    onCatalog = {
                        catalog = GalleryCatalogStore.load(this@MainActivity)
                        screen = GalleryPage.CATALOG
                    },
                    mailConfigured = email.isNotBlank() && password.isNotBlank(),
                    folderConfigured = folderPermissionReady,
                    folderName = treeName,
                    onMailSettings = { openSettings("Mail WebDAV") },
                    onFolderSettings = { openSettings("Папка Android") },
                    latestTransfer = latestTransfer(),
                    onLatestTransfer = {
                        screen = if (savedQueue != null) GalleryPage.QUEUE else GalleryPage.HISTORY
                    }
                    )
                }
                GalleryPage.CATALOG -> GalleryCatalog(
                    entries = catalog,
                    onBatchReview = ::batchBegin,
                    queue = savedQueue,
                    onQueue = { screen = GalleryPage.QUEUE },
                    onOpen = { gallery ->
                        setGalleryUrl(gallery.url)
                        model = gallery.model
                        title = gallery.title
                        date = gallery.date
                        screen = GalleryPage.HOME
                    },
                    onRemove = { gallery ->
                        if (GalleryCatalogStore.remove(this@MainActivity, gallery.url)) {
                            catalog = GalleryCatalogStore.load(this@MainActivity)
                        }
                    },
                    onEdit = { gallery, fields ->
                        try {
                            GalleryMetadataStore.save(this@MainActivity, gallery.url, fields)
                            check(GalleryCatalogStore.remember(this@MainActivity, gallery.url, fields))
                            catalog = GalleryCatalogStore.load(this@MainActivity)
                            if (GalleryKeys.same(url, gallery.url)) {
                                model = fields.model
                                title = fields.title
                                date = fields.date
                            }
                            true
                        } catch (_: Exception) { false }
                    },
                    onExport = {
                        val filename = "GalleryBridge-backup-" +
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"
                        catalogExportPicker.launch(filename)
                    },
                    onImport = { catalogImportPicker.launch(arrayOf("application/json", "text/plain")) },
                    backupStatus = catalogBackupStatus,
                    historyCount = history.size
                )
                GalleryPage.DOWNLOADS -> DownloadsPage(searchAllowed, startSearch)
                GalleryPage.QUEUE -> GalleryPageColumn { QueuePage() }
                GalleryPage.HISTORY -> GalleryPageColumn { HistoryPage() }
                GalleryPage.SETTINGS -> GalleryPageColumn { SettingsPage() }
            }
        }
        pendingCatalogImport?.let { staged ->
            val galleriesPlan = remember(staged, catalog) {
                GalleryCatalogBackup.plan(catalog, staged.galleries)
            }
            val historyPlan = remember(staged, history) {
                PortableHistoryBackup.plan(history, staged.history)
            }
            AlertDialog(
                onDismissRequest = { pendingCatalogImport = null },
                title = { Text("Восстановить данные?") },
                text = {
                    Text(
                        "Галерей: ${galleriesPlan.added} новых, ${galleriesPlan.alreadyPresent} совпадений. " +
                            "История: ${historyPlan.added} новых записей, ${historyPlan.alreadyPresent} совпадений. " +
                            "Не поместилось: ${galleriesPlan.noSpace + historyPlan.noSpace}. " +
                            "Тема: " + when (staged.darkTheme) {
                                true -> "тёмная"
                                false -> "светлая"
                                null -> "не меняется"
                            } + ". Существующие записи не перезаписываются. " +
                            "Очереди, пароли и разрешения папок не импортируются."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        catalogBackupStatus = try {
                            val importedHistory = HistoryStore.importMissing(this@MainActivity, staged.history)
                            history = HistoryStore.load(this@MainActivity)
                            val importedGalleries = GalleryCatalogStore.importMissing(this@MainActivity, staged.galleries)
                            catalog = GalleryCatalogStore.load(this@MainActivity)
                            staged.darkTheme?.let(::setDarkTheme)
                            "Восстановлено галерей: ${importedGalleries.added}; " +
                                "записей истории: ${importedHistory.added}. " +
                                "Не поместилось: ${importedGalleries.noSpace + importedHistory.noSpace}."
                        } catch (e: Exception) {
                            history = HistoryStore.load(this@MainActivity)
                            catalog = GalleryCatalogStore.load(this@MainActivity)
                            "Импорт завершился с ошибкой: ${e.message}. " +
                                "Проверьте каталог и историю: часть записей могла сохраниться."
                        }
                        pendingCatalogImport = null
                    }) { Text("Восстановить") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCatalogImport = null }) {
                        Text("Отмена")
                    }
                }
            )
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
        val listing = modelListing
        if (listing != null && items.isEmpty()) {
            ModelListingReview(
                result = listing, existing = catalog,
                selected = modelListingSelected, message = status,
                onSelect = { link, checked ->
                    modelListingSelected = if (checked) {
                        if (modelListingSelected.size < 100) modelListingSelected + link
                        else modelListingSelected
                    } else modelListingSelected - link
                },
                onSave = ::saveListingSelection,
                onOpen = ::openListingPublication,
                onBack = { screen = GalleryPage.HOME }
            )
            return
        }
        if (items.isEmpty()) {
            GalleryPageColumn {
                GalleryLinkField(url, ::setGalleryUrl, searchAllowed, startSearch)
                FindMediaButton(
                    searchAllowed && url.isNotBlank(), startSearch,
                    if (ModelListingSources.accepts(url)) "Найти фотопубликации" else "Найти фотографии"
                )
                OutlinedButton(onClick = ::pickLocalMedia, enabled = searchAllowed,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Добавить фотографии с устройства")
                }
                Text(status, style = MaterialTheme.typography.bodyMedium)
                if (scanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = ::stopSearch) { Text("Остановить поиск") }
                }
                QueueShortcut()
                BatchReviewControls()
                if (GalleryKeys.canonical(url) != null) {
                    var metadataExpanded by rememberSaveable(url) { mutableStateOf(false) }
                    GalleryDisclosure("Данные публикации", metadataExpanded) { metadataExpanded = !metadataExpanded }
                    if (metadataExpanded) GalleryFieldsForm(searchAllowed)
                }
            }
            return
        }

        var metadataExpanded by rememberSaveable(url) { mutableStateOf(false) }
        var checksExpanded by rememberSaveable(url) { mutableStateOf(false) }
        val candidates = remember(items, showOtherMedia, recommendedUrls) {
            MediaResults.visible(items, showOtherMedia, recommendedUrls)
        }
        val otherLinks = remember(items, recommendedUrls) {
            MediaResults.extraCount(items, recommendedUrls)
        }
        var pageLimit by rememberSaveable(url) { mutableIntStateOf(MediaResults.PAGE_SIZE) }
        // Only extra variants are paged. The recommended set is always shown in full.
        val visibleItems = remember(candidates, showOtherMedia, pageLimit) {
            if (showOtherMedia) MediaResults.page(candidates, pageLimit) else candidates
        }
        val photos = visibleItems.size
        val remainingInList = (candidates.size - photos).coerceAtLeast(0)
        // Pagination is not the same as the hidden-alternative filter: avoid
        // warning about photos merely waiting on another page.
        val hiddenSelected = remember(selected, candidates) {
            MediaResults.hiddenSelectionCount(selected, candidates)
        }
        val gridState = rememberLazyGridState()
        val fontScale = LocalDensity.current.fontScale
        val justified = true
        var openedPhoto by rememberSaveable(url) { mutableStateOf<String?>(null) }
        val aspectRatios = remember(url) { mutableStateMapOf<String, Float>() }

        BoxWithConstraints(Modifier.widthIn(max = GalleryContentWidth).fillMaxSize()) {
        val ratioSnapshot = aspectRatios.toMap()
        val justifiedRows = remember(visibleItems, ratioSnapshot, maxWidth, fontScale, justified) {
            if (justified) JustifiedRows.pack(
                photos = visibleItems,
                aspectRatios = ratioSnapshot,
                availableWidth = (maxWidth.value - 32f).coerceAtLeast(48f),
                targetHeight = if (fontScale > 1.3f) 190f else 154f,
                spacing = 6f
            ) else emptyList()
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (fontScale > 1.3f) 144.dp else 104.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (remainingInList > 0) "Фотографии: $photos из ${candidates.size}"
                        else "Фотографии: $photos",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        if (otherLinks > 0)
                            "Всего найдено: ${items.size} · Дополнительных вариантов: $otherLinks"
                        else "Все найденные фотографии показаны ниже",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (otherLinks > 0) {
                        TextButton(
                            onClick = {
                                showOtherMedia = !showOtherMedia
                                pageLimit = MediaResults.PAGE_SIZE
                            },
                            enabled = searchAllowed
                        ) {
                            Text(if (showOtherMedia) "Только рекомендуемые"
                                else "Просмотреть дополнительные варианты ($otherLinks)")
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                selected = selected + visibleItems.map(Media::url)
                            },
                            enabled = searchAllowed && visibleItems.any { it.url !in selected }
                        ) { Text("Выбрать показанные") }
                        TextButton(onClick = { selected = emptySet() }, enabled = searchAllowed && selected.isNotEmpty()) {
                            Text("Снять выделение")
                        }
                    }
                    if (hiddenSelected > 0) {
                        Text(
                            "Выбрано скрытых вариантов: $hiddenSelected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = {
                            // Preserve selections on other loaded or not-yet-loaded pages.
                            val allowed = candidates.map(Media::url).toSet()
                            selected = selected.intersect(allowed)
                        }, enabled = searchAllowed) {
                            Text("Снять выбор скрытых")
                        }
                    }
                    TextButton(onClick = { screen = GalleryPage.HOME }) {
                        Text("На главную")
                    }
                    if (transferRunning) {
                        Text(
                            "Идёт передача. Изменять выбор можно после её завершения.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    QueueShortcut()
                    BatchReviewControls()
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GalleryDisclosure("Данные публикации", metadataExpanded) { metadataExpanded = !metadataExpanded }
                    if (metadataExpanded) GalleryFieldsForm(searchAllowed)
                    GalleryDisclosure("Разрешение и варианты", checksExpanded) { checksExpanded = !checksExpanded }
                    if (checksExpanded) DownloadQualityControls(searchAllowed)
                }
            }
                items(justifiedRows, span = { GridItemSpan(maxLineSpan) },
                    key = { row -> "justified:${row.photos.first().url}" }) { row ->
                    JustifiedPhotoRow(
                        row = row,
                        selected = selected,
                        enabled = searchAllowed,
                        spacing = 6.dp,
                        onOpen = { openedPhoto = it.url },
                        onSelection = { media, checked ->
                            selected = if (checked) selected + media.url else selected - media.url
                        },
                        onAspectRatio = { media, ratio ->
                            if (aspectRatios[media.url] != ratio) aspectRatios[media.url] = ratio
                        }
                    )
                }
            if (showOtherMedia && remainingInList > 0) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "load-more-photo-variants") {
                    OutlinedButton(
                        onClick = {
                            pageLimit = (pageLimit + MediaResults.PAGE_SIZE).coerceAtMost(candidates.size)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = searchAllowed
                    ) {
                        val batch = minOf(remainingInList, MediaResults.PAGE_SIZE)
                        Text("Показать ещё $batch · Осталось $remainingInList")
                    }
                }
            }
        }
        }
        openedPhoto?.let { photoUrl ->
            val index = visibleItems.indexOfFirst { it.url == photoUrl }
            if (index >= 0) {
                FullscreenPhotoViewer(
                    photos = visibleItems,
                    initialIndex = index,
                    selected = selected,
                    onSelection = { media, checked ->
                        selected = if (checked) selected + media.url else selected - media.url
                    },
                    onClose = { openedPhoto = null }
                )
            } else {
                LaunchedEffect(photoUrl) { openedPhoto = null }
            }
        }
        if (showDestination) DestinationDialog()
    }

    @Composable
    private fun BatchReviewControls() {
        GalleryBatchReviewBar(
            position = batchReviewIndex, total = batchReviewUrls.size,
            canAdvance = !working && !scanning && !transferRunning && !imageProbeRunning &&
                (savedQueue?.remaining ?: 0) == 0,
            onNext = ::batchNext,
            onClose = ::batchClose
        )
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
    private fun GalleryFieldsForm(enabled: Boolean, allowSave: Boolean = true) {
        OutlinedTextField(model, { model = it }, label = { Text("Имя модели") },
            modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true)
        OutlinedTextField(title, { title = it }, label = { Text("Название публикации") },
            modifier = Modifier.fillMaxWidth(), enabled = enabled)
        OutlinedTextField(date, { date = it }, label = { Text("Дата ГГГГ-ММ-ДД") },
            modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            isError = !GalleryKeys.validDate(date.trim()),
            supportingText = if (GalleryKeys.validDate(date.trim())) null else { { Text("Укажите реальную дату") } })
        if (suggestedPublicationDate.isNotBlank() && date.isBlank()) {
            Text("Предполагаемая дата: $suggestedPublicationDate",
                style = MaterialTheme.typography.bodyMedium)
            Text(suggestedPublicationDateSource,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                date = suggestedPublicationDate
                suggestedPublicationDate = ""
                suggestedPublicationDateSource = ""
                metadataReport = "Предполагаемая дата применена по вашему выбору. Источник не подтверждает дату публикации."
            }, enabled = enabled) { Text("Использовать предполагаемую дату") }
        }
        if (allowSave) {
            val fields = GalleryFields(model.trim(), title.trim(), date.trim())
            val saved = GalleryCatalogStore.hasSavedFields(catalog, url, fields)
            if (saved) {
                Text(
                    "Данные сохранены в каталоге",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                if (metadataReport.isNotBlank()) {
                    Text(metadataReport, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = ::saveGalleryMetadata,
                    enabled = enabled && GalleryKeys.canonical(url) != null &&
                        fields.model.isNotBlank() && fields.title.isNotBlank() &&
                        GalleryKeys.validDate(fields.date),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (GalleryCatalogStore.contains(catalog, url)) "Сохранить изменения"
                    else "Сохранить данные галереи") }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun DownloadQualityControls(searchAllowed: Boolean) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(
                onClick = { selected = recommendedUrls },
                enabled = searchAllowed && recommendedUrls.isNotEmpty() &&
                    selected != recommendedUrls
            ) { Text("Выбрать рекомендованные") }
            OutlinedButton(
                onClick = ::verifyImageSizes,
                enabled = searchAllowed && ImageProbePlan.selectedPhotoCount(items, selected) > 0
            ) { Text("Проверить размеры") }
            OutlinedButton(
                onClick = ::compareImageVariants,
                enabled = searchAllowed && selected.isNotEmpty()
            ) { Text("Сравнить качество") }
        }
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
        if (showRepeatConfirmation) {
            val selectedPhotos = items.filter { it.kind == Kind.PHOTO && it.url in selected }
            val matched = runCatching {
                PhotoOutcomeLedger.matchedUrls(
                    this@MainActivity, url, target,
                    if (target == "Папка Android") treeUri else mailRoot,
                    if (target == "Mail WebDAV") email.trim() else "",
                    selectedPhotos, QueueStore.load(this@MainActivity)
                )
            }.getOrElse {
                showRepeatConfirmation = false
                uploadStatus = "Не удалось проверить журнал фотографий: ${it.message}"
                return
            }
            GalleryRepeatReviewDialog(
                onConfirm = {
                    repeatConsent = repeatReviewKey()
                    showRepeatConfirmation = false
                    beginUpload()
                },
                onCancel = {
                    showRepeatConfirmation = false
                    repeatConsent = null
                },
                matchedCount = matched.size,
                totalCount = selectedPhotos.size,
                onOnlyUnmarked = if (matched.isNotEmpty() && matched.size < selectedPhotos.size) {
                    {
                        selected = selected - matched
                        repeatConsent = repeatReviewKey()
                        showRepeatConfirmation = false
                        beginUpload()
                    }
                } else null
            )
            return
        }
        var showFields by remember(url) {
            mutableStateOf(model.isBlank() || title.isBlank() || !GalleryKeys.validDate(date.trim()))
        }
        val enabled = !working && !transferRunning
        AlertDialog(
            onDismissRequest = { if (enabled) showDestination = false },
            title = { Text("Куда загрузить") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DestinationPicker(target, enabled) {
                        target = it
                        repeatConsent = null
                        uploadStatus = ""
                    }
                    val folder = when (target) {
                        "Mail WebDAV" -> mailRoot.ifBlank { "Корневая папка Mail" }
                        else -> treeName
                    }
                    Text(folder, style = MaterialTheme.typography.bodyMedium)
                    if (target == "Папка Android" && !folderPermissionReady && treeUri.isNotBlank()) {
                        Text(folderPermissionMessage, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { openSettings(target) }, enabled = enabled) {
                        Text("Настроить назначение")
                    }
                    Text(
                        "Публикация: " + model.ifBlank { "Без имени" } +
                            if (date.isBlank()) "" else " · $date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    GalleryDisclosure("Данные публикации", showFields) { showFields = !showFields }
                    if (showFields) {
                        GalleryFieldsForm(enabled, allowSave = false)
                        Text("Данные публикации сохраняются при начале загрузки.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
            FilterChip(destination == "Папка Android", { select("Папка Android") }, { Text("Папка Android / облака") }, enabled = enabled)
            FilterChip(destination == "Mail WebDAV", { select("Mail WebDAV") }, { Text("Mail WebDAV") }, enabled = enabled)
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
            else -> {
                Text(treeName, style = MaterialTheme.typography.bodyMedium)
                if (treeUri.isNotBlank() && !folderPermissionReady) {
                    Text(folderPermissionMessage, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "Папку Google Диска можно выбрать без OAuth, если облачный провайдер " +
                        "поддерживает запись через Android. Синхронизация с облаком может продолжиться позже.",
                    style = MaterialTheme.typography.bodySmall
                )
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
        if (packageName == "com.gallerybridge.app.preview.signed") UpdateSettings(updater)
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
        pendingUnblock?.takeIf { it.first == queue.id }?.let { request ->
            val item = queue.entries.getOrNull(request.second)
            if (item != null && item.state == "BLOCKED") {
                AlertDialog(
                    onDismissRequest = { pendingUnblock = null },
                    title = { Text("Разрешить повтор этого файла?") },
                    text = {
                        Text("Сначала проверьте файл в назначении. Если запись была неполной, " +
                            "удалите её самостоятельно или исправьте в провайдере. " +
                            "Приложение пропускает существующие файлы по имени и не проверяет их содержимое. " +
                            "Подтвердите только после ручной проверки; другие файлы очередь не изменит.")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (transferRunning || getSharedPreferences(TransferService.PREFS, MODE_PRIVATE)
                                    .getBoolean(TransferService.KEY_RUNNING, false)) {
                                transferStatus = "Дождитесь остановки текущей передачи."
                            } else {
                                try {
                                    val ok = QueueStore.allowRetryAfterReview(this@MainActivity,
                                        queue.id, request.second, item.media)
                                    savedQueue = QueueStore.load(this@MainActivity)
                                    transferStatus = if (ok) "Повтор файла разрешён после подтверждения."
                                        else "Элемент очереди изменился. Обновите экран."
                                } catch (e: Exception) {
                                    transferStatus = "Не удалось сохранить очередь: " + e.message.orEmpty()
                                }
                            }
                            pendingUnblock = null
                        }) { Text("Я проверил файл") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingUnblock = null }) { Text("Отмена") }
                    }
                )
            }
        }
        val done = queue.entries.count { it.state == "DONE" }
        val skipped = queue.entries.count { it.state == "SKIPPED" }
        val failed = queue.entries.count { it.state == "FAILED" || it.state == "BLOCKED" }
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
                if (queue.blocked > 0) {
                    Text("Требуют ручной проверки: " + queue.blocked,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
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
            Button(onClick=::resumeUpload, enabled = !working && queue.retryable > 0, modifier=Modifier.fillMaxWidth()) { Text("Продолжить безопасно: ${queue.retryable} файлов") }
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
        val filtered = queue.entries.withIndex().filter { (_, entry) ->
            when (queueFilter) {
                "PENDING" -> entry.state == "PENDING" || entry.state == "FAILED"
                "DONE" -> entry.state == "DONE" || entry.state == "SKIPPED"
                "FAILED" -> entry.state == "FAILED" || entry.state == "BLOCKED"
                else -> true
            }
        }
        if (filtered.isEmpty()) Text("В этой категории файлов нет.")
        filtered.take(80).forEach { (index, item) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(when(item.state) {
                    "DONE" -> "✓"
                    "SKIPPED" -> "↷"
                    "FAILED", "BLOCKED" -> "!"
                    else -> "·"
                })
                Column(Modifier.weight(1f)) {
                    Text(item.media.name, style=MaterialTheme.typography.bodyMedium)
                    Text(when(item.state) {
                        "DONE" -> if (queue.target == "Папка Android") "Передан провайдеру" else "Загружен"
                        "SKIPPED" -> if (queue.target == "Папка Android") "Существует у провайдера" else "Уже был в облаке"
                        "FAILED" -> "Ошибка, безопасный повтор доступен"
                        "BLOCKED" -> "Требует проверки перед повтором"
                        else -> "Ожидает"
                    }, style=MaterialTheme.typography.bodySmall)
                    if (item.failureReason.isNotBlank()) {
                        Text(item.failureReason, style = MaterialTheme.typography.bodySmall)
                    }
                    if (item.state == "BLOCKED") {
                        TextButton(onClick = { pendingUnblock = queue.id to index },
                            enabled = !transferRunning && !working) {
                            Text("Разрешить повтор после проверки")
                        }
                    }
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
}
