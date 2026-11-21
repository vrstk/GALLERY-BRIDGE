package com.gallerybridge.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gallerybridge.app.core.*
import com.gallerybridge.app.network.ImageQualityProbe
import com.gallerybridge.app.transfer.*
import com.gallerybridge.app.ui.GalleryPage
import com.gallerybridge.app.update.UpdateManager
import kotlinx.coroutines.Job

/** UI session and ongoing scans survive Activity recreation (e.g. rotation). */
internal class GallerySessionState(application: Application) : AndroidViewModel(application) {
    var initialized = false
    var url by mutableStateOf("")
    var model by mutableStateOf("")
    var title by mutableStateOf("")
    var date by mutableStateOf("")
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var treeUri by mutableStateOf("")
    var treeName by mutableStateOf("Папка не выбрана")
    var folderPermissionReady by mutableStateOf(false)
    var folderPermissionMessage by mutableStateOf("")
    var mailRoot by mutableStateOf("Models")
    var target by mutableStateOf("Папка Android")
    var dark by mutableStateOf(true)
    var working by mutableStateOf(false)
    var scanning by mutableStateOf(false)
    var modelListing by mutableStateOf<ModelListingSources.Result?>(null)
    var modelListingSelected by mutableStateOf<Set<String>>(emptySet())
    var status by mutableStateOf("Вставьте HTTPS-ссылку на фотогалерею.")
    var items by mutableStateOf<List<Media>>(emptyList())
    var selected by mutableStateOf<Set<String>>(emptySet())
    var videoPosterUrl by mutableStateOf("")
    /** Current full-screen photo URL; kept across Activity recreation. */
    var openedPhoto by mutableStateOf<String?>(null)
    var reveal by mutableStateOf(false)
    var rememberMailPassword by mutableStateOf(false)
    var showOtherMedia by mutableStateOf(false)
    var mediaFilter by mutableStateOf<Kind?>(null)
    var showDestination by mutableStateOf(false)
    var uploadStatus by mutableStateOf("")
    var recommendedUrls by mutableStateOf<Set<String>>(emptySet())
    var imageProbeRunning by mutableStateOf(false)
    var imageProbeProgress by mutableStateOf("")
    var imageProbeStatus by mutableStateOf("")
    var imageProbeResults by mutableStateOf<Map<String, ImageQualityProbe.Result>>(emptyMap())
    var comparisonReport by mutableStateOf("")
    var metadataReport by mutableStateOf("")
    var suggestedPublicationDate by mutableStateOf("")
    var suggestedPublicationDateSource by mutableStateOf("")
    var reportDetails by mutableStateOf("")
    var showReportDetails by mutableStateOf(false)
    var transferRunning by mutableStateOf(false)
    var savedQueue by mutableStateOf<QueueStore.Snapshot?>(null)
    var history by mutableStateOf<List<HistoryStore.Record>>(emptyList())
    var catalog by mutableStateOf<List<CatalogGallery>>(emptyList())
    var catalogBackupStatus by mutableStateOf("")
    var batchReviewUrls by mutableStateOf<List<String>>(emptyList())
    var batchReviewIndex by mutableIntStateOf(0)
    var pendingCatalogImport by mutableStateOf<GalleryPortableBackup.Payload?>(null)
    var screen by mutableStateOf(GalleryPage.HOME)
    var settingsDestination by mutableStateOf("Mail WebDAV")
    var settingsStatus by mutableStateOf("")
    var transferStatus by mutableStateOf("")
    var queueFilter by mutableStateOf("ALL")
    var showCloseQueueConfirmation by mutableStateOf(false)
    var showRepeatConfirmation by mutableStateOf(false)
    var pendingUnblock by mutableStateOf<Pair<Long, Int>?>(null)
    var pendingRetry by mutableStateOf<Pair<Long, Int>?>(null)
    var singleRetryIndex: Int? = null
    var auditing by mutableStateOf(false)
    var auditProgress by mutableStateOf("")
    var auditReport by mutableStateOf("")
    var scanJob: Job? = null
    var imageProbeJob: Job? = null
    var settingsOrigin = GalleryPage.HOME
    var resumeRequest = false

    /** Application context avoids retaining a destroyed Activity during an update. */
    val updater by lazy {
        UpdateManager(application, viewModelScope) {
            transferRunning || application
                .getSharedPreferences(TransferService.PREFS, Context.MODE_PRIVATE)
                .getBoolean(TransferService.KEY_RUNNING, false)
        }
    }
}
