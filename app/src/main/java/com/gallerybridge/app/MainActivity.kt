package com.gallerybridge.app

import android.content.Intent
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
    private var showOtherMedia by mutableStateOf(false)
    private var reportDetails by mutableStateOf("")
    private var showReportDetails by mutableStateOf(false)

    private val auth = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        try { val token = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(r.data).accessToken
            if (token.isNullOrEmpty()) { working = false; status = "Google: нет токена" } else upload(token)
        } catch (e: Exception) { working = false; status = e.message ?: "Ошибка Google OAuth" }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = getSharedPreferences("settings", MODE_PRIVATE)
        model = p.getString("model", "") ?: ""
        email = p.getString("email", "") ?: ""
        driveId = p.getString("driveId", driveId) ?: driveId
        mailRoot = p.getString("mailRoot", "Models") ?: "Models"
        dark = p.getBoolean("dark", false)
        url = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty().takeIf { it.startsWith("https://") }.orEmpty()
        setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) { Surface(Modifier.fillMaxSize().safeDrawingPadding()) { Ui() } } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); intent.getStringExtra(Intent.EXTRA_TEXT)?.let { if (it.startsWith("https://")) url = it } }
    private fun scan() {
        if (working) return
        working = true; status = "Проверка страницы..."; items = emptyList(); selected = emptySet(); showOtherMedia = false; reportDetails = ""; showReportDetails = false
        scope.launch { try {
            val s = Services.scan(url)
            title = s.title; date = s.date; items = s.media
            selected = s.media.filter { it.source in setOf("Ссылка", "Видео", "Прямой URL", "Прямой файл") }.map { it.url }.toSet()
            status = "Найдено ${items.size} медиа-ссылок. Выбрано ${selected.size} ссылок на файлы; остальные изображения могут быть миниатюрами. Оригинальное качество не подтверждено."
        } catch (e: Exception) { status = "Ошибка поиска: ${e.message}" } finally { working = false } }
    }
    private fun upload(token: String?) {
        working = true
        reportDetails = ""
        showReportDetails = false
        val chosen = items.filter { it.url in selected }
        scope.launch {
            try {
                val result = Services.transfer(
                    applicationContext, chosen, model, title, date, target,
                    email, password, if (target == "Google Drive") driveId else mailRoot, token
                ) { status = it }
                status = result.substringBefore('\n')
                reportDetails = result.substringAfter('\n', "")
            } catch (e: Exception) {
                status = "Ошибка: ${e.message}"
            } finally {
                working = false
            }
        }
    }
    private fun beginUpload() {
        if (selected.isEmpty()) { status = "Нет выбранных файлов"; return }
        if (model.isBlank() || title.isBlank()) { status = "Укажите имя модели и название публикации перед загрузкой"; return }
        if (target == "Mail WebDAV") {
            if (email.isBlank() || password.isBlank()) { status = "Укажите почту и пароль приложения Mail"; return }
            upload(null); return
        }
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
                if (t == null) { working = false; status = "Google: нет токена" } else upload(t)
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
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row { Text("GALLERY BRIDGE", style=MaterialTheme.typography.headlineSmall, modifier=Modifier.weight(1f))
                TextButton(onClick={dark=!dark}) { Text(if (dark) "Светлая" else "Тёмная") } }
            HorizontalDivider()
            OutlinedTextField(url, {url=it}, label={Text("HTTPS-ссылка")}, modifier=Modifier.fillMaxWidth())
            Button(onClick=::scan, enabled=!working && url.isNotBlank(), modifier=Modifier.fillMaxWidth()) { Text("Найти фото и видео") }
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
            if (working) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
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
                OutlinedTextField(email,{email=it}, label={Text("Почта Mail")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(password,{password=it},label={Text("Пароль внешнего приложения")},modifier=Modifier.fillMaxWidth(),visualTransformation=if(reveal) VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={TextButton(onClick={reveal=!reveal}){Text(if(reveal)"Скрыть" else "Показать")}})
                OutlinedTextField(mailRoot,{mailRoot=it},label={Text("Путь в Облаке Mail")},modifier=Modifier.fillMaxWidth(),supportingText={ Text("Папки разделяются /, например Images/xxx/") })
            } else {
                OutlinedTextField(driveId,{driveId=it},label={Text("ID папки Google Drive")},modifier=Modifier.fillMaxWidth())
                Text("Для Google OAuth необходимы Google Cloud Drive API, Android package name и SHA-1 подписи APK.", style=MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick={
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("model",model).putString("email",email).putString("driveId",driveId).putString("mailRoot",mailRoot).putBoolean("dark",dark).apply()
                status="Настройки сохранены (пароль не сохраняется)"
            },modifier=Modifier.fillMaxWidth()){Text("Сохранить настройки")}
            Button(onClick=::beginUpload,enabled=!working&&selected.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Скачать и загрузить")}
            Text("MVP v0.3 • Mail WebDAV: повторные попытки при временных сетевых ошибках. Google Drive требует зарегистрированный OAuth-клиент.",style=MaterialTheme.typography.bodySmall)
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
