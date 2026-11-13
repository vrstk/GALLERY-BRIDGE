package com.gallerybridge.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.gallerybridge.app.core.*
import com.gallerybridge.app.network.Services
import com.gallerybridge.app.transfer.TransferService
import com.gallerybridge.app.transfer.QueueStore
import com.gallerybridge.app.transfer.HistoryStore
import com.gallerybridge.app.security.SecureMailCredentials
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
    private var mailRoot by mutableStateOf("Models")
    private var target by mutableStateOf("Mail WebDAV")
    private var dark by mutableStateOf(false)
    private var working by mutableStateOf(false)
    private var status by mutableStateOf("Вставьте HTTPS-ссылку на галерею или файл.")
    private var items by mutableStateOf<List<Media>>(emptyList())
    private var selected by mutableStateOf<Set<String>>(emptySet())
    private var reveal by mutableStateOf(false)
    private var rememberMailPassword by mutableStateOf(false)
    private var showOtherMedia by mutableStateOf(false)
    private var reportDetails by mutableStateOf("")
    private var showReportDetails by mutableStateOf(false)
    private var transferRunning by mutableStateOf(false)
    private var notificationToken: String? = null
    private var resumeRequest = false
    private var savedQueue by mutableStateOf<QueueStore.Snapshot?>(null)
    private var history by mutableStateOf<List<HistoryStore.Record>>(emptyList())
    private var screen by mutableIntStateOf(0)
    private var queueFilter by mutableStateOf("ALL")

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Notification permission is optional for running a dataSync foreground service.
        if (resumeRequest) beginResumeTransfer(notificationToken) else beginServiceTransfer(notificationToken)
        resumeRequest = false
        notificationToken = null
    }

    private val auth = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        try { val token = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(r.data).accessToken
            if (token.isNullOrEmpty()) { working = false; status = "Google: нет токена" } else if (resumeRequest) { resumeRequest = false; uploadResume(token) } else upload(token)
        } catch (e: Exception) { working = false; status = e.message ?: "Ошибка Google OAuth" }
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
        mailRoot = p.getString("mailRoot", "Models") ?: "Models"
        dark = p.getBoolean("dark", false)
        savedQueue = QueueStore.load(this)
        history = HistoryStore.load(this)
        transferRunning = getSharedPreferences(TransferService.PREFS, MODE_PRIVATE)
            .getBoolean(TransferService.KEY_RUNNING, false)
        url = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty().takeIf { it.startsWith("https://") }.orEmpty()
        setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) { Surface(Modifier.fillMaxSize().safeDrawingPadding()) { Ui() } } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); intent.getStringExtra(Intent.EXTRA_TEXT)?.let { if (it.startsWith("https://")) url = it } }
    private fun scan() {
        if (working || transferRunning) return
        working = true; status = "Проверка страницы..."; items = emptyList(); selected = emptySet(); showOtherMedia = false; reportDetails = ""; showReportDetails = false
        scope.launch { try {
            val s = Services.scan(url)
            title = s.title; date = s.date; items = s.media
            selected = s.media.filter { it.source in setOf("Ссылка", "Видео", "Прямой URL", "Прямой файл") }.map { it.url }.toSet()
            status = "Найдено ${items.size} медиа-ссылок. Выбрано ${selected.size} ссылок на файлы; остальные изображения могут быть миниатюрами. Оригинальное качество не подтверждено."
        } catch (e: Exception) { status = "Ошибка поиска: ${e.message}" } finally { working = false } }
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
                if (target == "Google Drive") driveId else mailRoot, token
            )
            savedQueue = QueueStore.load(this)
            transferRunning = true
            status = "Фоновая передача запущена. Можно переключаться на другие приложения."
        } catch (e: Exception) {
            status = "Не удалось запустить фоновую передачу: ${e.message}"
        } finally {
            working = false
        }
    }
    private fun beginResumeTransfer(token: String?) {
        try {
            val queue = QueueStore.load(this) ?: error("Очередь не найдена")
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
            status = "Продолжаем сохранённую очередь"
        } catch (e: Exception) {
            status = "Возобновление: ${e.message}"
        } finally { working = false }
    }

    private fun resumeUpload() {
        val queue = QueueStore.load(this) ?: run { status = "Нет сохранённой очереди"; return }
        if (transferRunning || working || queue.remaining == 0) return
        if (queue.target == "Mail WebDAV") {
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
        beginGoogleAuthorization()
    }

    private fun beginGoogleAuthorization() {
        working = true; status = "Запрос доступа Google Drive..."
        Identity.getAuthorizationClient(this).authorize(
            AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive"))).build()
        ).addOnSuccessListener { a ->
            if (a.hasResolution()) {
                val p = a.pendingIntent
                if (p != null) auth.launch(IntentSenderRequest.Builder(p.intentSender).build())
                else { working = false; status = "Google OAuth недоступен" }
            } else {
                val t = a.accessToken
                if (t == null) { working = false; status = "Google: нет токена" } else if (resumeRequest) { resumeRequest = false; uploadResume(t) } else upload(t)
            }
        }.addOnFailureListener {
            working = false
            val msg = it.message.orEmpty()
            status = if (msg.contains("UNREGISTERED_ON_API_CONSOLE", true) || msg.contains("DEVELOPER_ERROR", true))
                "Google Drive: OAuth-клиент приложения не зарегистрирован. Нужны Google Cloud Drive API, package com.gallerybridge.app и SHA-1 именно установленной APK."
                else "Ошибка авторизации Google: $msg"
        }
    }
    @Composable private fun Ui() {
        LaunchedEffect(Unit) {
            var lastRevision = -1L
            while (isActive) {
                val p = getSharedPreferences(TransferService.PREFS, MODE_PRIVATE)
                val revision = p.getLong(TransferService.KEY_REVISION, 0L)
                if (revision > 0L && revision != lastRevision) {
                    lastRevision = revision
                    transferRunning = p.getBoolean(TransferService.KEY_RUNNING, false)
                    status = p.getString(TransferService.KEY_STATUS, "").orEmpty()
                    reportDetails = p.getString(TransferService.KEY_DETAILS, "").orEmpty()
                    savedQueue = QueueStore.load(this@MainActivity)
                    history = HistoryStore.load(this@MainActivity)
                }
                delay(800)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row { Text("GALLERY BRIDGE", style=MaterialTheme.typography.headlineSmall, modifier=Modifier.weight(1f))
                TextButton(onClick={dark=!dark}) { Text(if (dark) "Светлая" else "Тёмная") } }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=screen == 0, onClick={ screen=0 }, label={Text("Загрузка")})
                FilterChip(selected=screen == 1, onClick={ screen=1 }, label={Text("Очередь")})
                FilterChip(selected=screen == 2, onClick={ screen=2; history = HistoryStore.load(this@MainActivity) }, label={Text("История")})
            }
            if (screen == 1) {
                QueuePage()
            } else if (screen == 2) {
                HistoryPage()
            } else {
            OutlinedTextField(url, {url=it}, label={Text("HTTPS-ссылка")}, modifier=Modifier.fillMaxWidth())
            Button(onClick=::scan, enabled=!working && !transferRunning && url.isNotBlank(), modifier=Modifier.fillMaxWidth()) { Text("Найти фото и видео") }
            Text(status)
            if (reportDetails.isNotBlank()) {
                TextButton(onClick = { showReportDetails = !showReportDetails }) {
                    Text(if (showReportDetails) "Скрыть подробности" else "Показать подробности и причины ошибок")
                }
                if (showReportDetails) {
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
                        Column(Modifier.fillMaxWidth().heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()).padding(12.dp)) {
                            Text(reportDetails, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (working || transferRunning) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
            val queueInfo = savedQueue
            if (!transferRunning && queueInfo != null && queueInfo.remaining > 0) {
                Text("Сохранённая очередь: ${queueInfo.completed} завершено, ${queueInfo.remaining} осталось. Модель: ${queueInfo.model}. Облако: ${queueInfo.target}.")
                if (queueInfo.target == "Mail WebDAV") {
                    if (queueInfo.account.isNotBlank()) {
                        Text("Аккаунт очереди: ${queueInfo.account}", style=MaterialTheme.typography.bodySmall)
                    }
                    if (password.isBlank()) {
                        OutlinedTextField(password, { password = it },
                            label={ Text("Пароль Mail для продолжения") },
                            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier=Modifier.fillMaxWidth())
                    }
                }
                Button(onClick=::resumeUpload, enabled=!working, modifier=Modifier.fillMaxWidth()) {
                    Text("Продолжить сохранённую очередь")
                }
                Text("Для продолжения потребуется прежний аккаунт облака; пароль не сохраняется.", style=MaterialTheme.typography.bodySmall)
            }
            if (transferRunning) {
                Text("Передача продолжается при сворачивании. Прогресс — в уведомлениях.", style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={ TransferService.cancel(this@MainActivity); transferRunning = false; status = "Остановка запрошена. Очередь сохранена." }) { Text("Остановить передачу") }
            }
            OutlinedTextField(model, {model=it}, label={Text("Имя модели")}, modifier=Modifier.fillMaxWidth())
            OutlinedTextField(title, {title=it}, label={Text("Название публикации")}, modifier=Modifier.fillMaxWidth())
            OutlinedTextField(date, {date=it}, label={Text("Дата YYYY-MM-DD")}, modifier=Modifier.fillMaxWidth())
            Text("Найдено: ${items.size}; выбрано: ${selected.size}")
            val visibleItems = items.filter { showOtherMedia || it.source in setOf("Ссылка", "Видео", "Прямой URL", "Прямой файл") }
            if (items.isNotEmpty()) {
                Row {
                    TextButton(onClick={selected=visibleItems.map { it.url }.toSet()}) { Text("Выбрать видимые") }
                    TextButton(onClick={selected=emptySet()}) { Text("Снять выделение") }
                }
                if (visibleItems.size < items.size || showOtherMedia) {
                    TextButton(onClick={showOtherMedia=!showOtherMedia}) {
                        Text(if (showOtherMedia) "Скрыть миниатюры и прочие" else "Показать остальные ${items.size-visibleItems.size} ссылок")
                    }
                }
            }
            visibleItems.forEach { item -> Row {
                Checkbox(item.url in selected, onCheckedChange={v -> selected=if(v) selected+item.url else selected-item.url}, enabled=!working)
                Column { Text(item.name); Text(item.source, style=MaterialTheme.typography.bodySmall) }
            }}
            Row { FilterChip(target=="Mail WebDAV", onClick={target="Mail WebDAV"}, label={Text("Mail WebDAV")})
                Spacer(Modifier.width(8.dp))
                FilterChip(target=="Google Drive", onClick={target="Google Drive"}, label={Text("Google Drive")}) }
            if (target=="Mail WebDAV") {
                OutlinedTextField(email,{ newEmail ->
                    if (newEmail != email) {
                        email = newEmail
                        val stored = SecureMailCredentials.load(this@MainActivity, newEmail)
                        if (stored != null) {
                            password = stored
                            rememberMailPassword = true
                        } else if (rememberMailPassword) {
                            password = ""
                            rememberMailPassword = false
                        }
                    }
                }, label={Text("Почта Mail")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(password,{password=it},label={Text("Пароль внешнего приложения")},modifier=Modifier.fillMaxWidth(),visualTransformation=if(reveal) VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={TextButton(onClick={reveal=!reveal}){Text(if(reveal)"Скрыть" else "Показать")}})
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=rememberMailPassword, onCheckedChange={ checked ->
                        rememberMailPassword = checked
                        if (!checked) SecureMailCredentials.clear(this@MainActivity)
                    })
                    Text("Запомнить пароль на этом устройстве")
                }
                Text("Пароль шифруется Android Keystore и не загружается в GitHub. Чтобы сохранить его, нажми «Сохранить настройки».", style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(mailRoot,{mailRoot=it},label={Text("Путь в Облаке Mail")},modifier=Modifier.fillMaxWidth(),supportingText={ Text("Папки разделяются /, например Images/xxx/") })
            } else {
                OutlinedTextField(driveId,{driveId=it},label={Text("ID папки Google Drive")},modifier=Modifier.fillMaxWidth())
                Text("Для Google OAuth необходимы Google Cloud Drive API, Android package name и SHA-1 подписи APK.", style=MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick={
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("model",model).putString("email",email).putString("driveId",driveId).putString("mailRoot",mailRoot).putBoolean("dark",dark).apply()
                status = try {
                    if (rememberMailPassword) {
                        SecureMailCredentials.save(this@MainActivity, email, password)
                        "Настройки и зашифрованный пароль Mail сохранены на устройстве"
                    } else {
                        SecureMailCredentials.clear(this@MainActivity)
                        "Настройки сохранены, пароль Mail не сохраняется"
                    }
                } catch (e: Exception) {
                    "Настройки сохранены, но сохранить пароль не удалось: ${e.message}"
                }
            },modifier=Modifier.fillMaxWidth()){Text("Сохранить настройки")}
            Button(onClick=::beginUpload,enabled=!working&&!transferRunning&&selected.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Скачать и загрузить")}
            Text("MVP v0.7 • История и состояние очереди по файлам. Уже сохранённая незавершённая очередь не заменяется другой.",style=MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable private fun QueuePage() {
        val queue = savedQueue
        if (queue == null) {
            Text("Пока нет сохранённых заданий. Вставь ссылку на вкладке «Загрузка».")
            return
        }
        val done = queue.entries.count { it.state == "DONE" }
        val skipped = queue.entries.count { it.state == "SKIPPED" }
        val failed = queue.entries.count { it.state == "FAILED" }
        val pending = queue.entries.count { it.state == "PENDING" }
        Text(queue.model, style=MaterialTheme.typography.titleLarge)
        Text(queue.title)
        Text("Облако: ${queue.target} · Файлов: ${queue.entries.size}", style=MaterialTheme.typography.bodySmall)
        if (queue.entries.isNotEmpty()) {
            LinearProgressIndicator(progress={ (done + skipped).toFloat() / queue.entries.size.toFloat() }, modifier=Modifier.fillMaxWidth())
        }
        Text("Загружено: $done · Уже было: $skipped · Ошибок: $failed · Ожидают: $pending")
        if (transferRunning) {
            Text("Передача выполняется в фоне")
            TextButton(onClick={ TransferService.cancel(this@MainActivity); transferRunning=false; status="Остановка запрошена" }) { Text("Остановить передачу") }
        } else if (queue.remaining > 0) {
            if (queue.target == "Mail WebDAV") {
                OutlinedTextField(email, {email=it}, label={Text("Аккаунт Mail для продолжения")}, modifier=Modifier.fillMaxWidth())
                if (password.isBlank()) {
                    OutlinedTextField(password, {password=it}, label={Text("Пароль приложения Mail")}, visualTransformation=PasswordVisualTransformation(), modifier=Modifier.fillMaxWidth())
                }
            }
            Button(onClick=::resumeUpload, modifier=Modifier.fillMaxWidth()) { Text("Продолжить оставшиеся ${queue.remaining} файлов") }
        } else {
            Text("Очередь завершена. Можно добавить следующую галерею.")
        }
        Text(status, style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
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
                        "DONE" -> "Загружен"
                        "SKIPPED" -> "Уже был в облаке"
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
        Text("Последние задания", style=MaterialTheme.typography.titleLarge)
        Text("Локальный журнал без паролей и токенов.", style=MaterialTheme.typography.bodySmall)
        history.forEach { record ->
            Surface(shape=MaterialTheme.shapes.medium, tonalElevation=1.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(record.model, style=MaterialTheme.typography.titleMedium)
                    Text(record.title, style=MaterialTheme.typography.bodyMedium)
                    Text("${record.target} · ${record.outcome}", style=MaterialTheme.typography.bodySmall)
                    Text("Загружено ${record.uploaded} · Было ${record.skipped} · Ошибок ${record.failed} · Осталось ${record.remaining}", style=MaterialTheme.typography.bodySmall)
                    Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(record.updatedAt)), style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
