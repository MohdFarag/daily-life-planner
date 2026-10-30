package app.dailylife.android.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.dailylife.android.AppViewModel
import app.dailylife.android.data.AudioRecorder
import app.dailylife.android.data.Media
import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CaptureKind
import app.dailylife.android.ui.attachmentLabel
import app.dailylife.android.ui.Btn
import app.dailylife.android.ui.BtnStyle
import app.dailylife.android.ui.C
import app.dailylife.android.ui.IconBtn
import app.dailylife.android.ui.R12
import app.dailylife.android.ui.R16
import app.dailylife.android.ui.T
import app.dailylife.android.ui.card
import app.dailylife.android.ui.clock
import app.dailylife.android.ui.num
import app.dailylife.android.ui.tr
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Unsaved capture survives the app being closed (CAP03). */
private object Draft {
    private fun prefs(c: Context) = c.getSharedPreferences("capture-draft", Context.MODE_PRIVATE)
    fun load(c: Context): Pair<String, List<Attachment>> {
        val o = prefs(c).getString("draft", null)?.let(::JSONObject) ?: return "" to emptyList()
        val a = o.optJSONArray("attachments") ?: JSONArray()
        return o.optString("text") to (0 until a.length()).map { a.getJSONObject(it) }
            .filter { File(it.getString("path")).exists() }
            .map { Attachment(it.getString("id"), it.getString("path"), it.getString("mime"), File(it.getString("path")).length()) }
    }
    fun save(c: Context, text: String, attachments: List<Attachment>) {
        val o = JSONObject().put("text", text).put("attachments", JSONArray(attachments.map { JSONObject().put("id", it.id).put("path", it.localPath).put("mime", it.mimeType) }))
        prefs(c).edit().putString("draft", o.toString()).apply()
    }
    fun clear(c: Context) = prefs(c).edit().clear().apply()
}

private enum class Mode(val key: String, val kind: CaptureKind) {
    TEXT("text", CaptureKind.TEXT), LIST("list", CaptureKind.LIST), VOICE("voice", CaptureKind.AUDIO),
    PHOTO("photo", CaptureKind.IMAGE), VIDEO("video", CaptureKind.VIDEO), FILE("file", CaptureKind.FILE),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(vm: AppViewModel) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initial = remember { Draft.load(context) }
    var mode by remember { mutableStateOf(Mode.TEXT) }
    var text by remember { mutableStateOf(initial.first) }
    val attachments = remember { mutableStateListOf<Attachment>().apply { addAll(initial.second) } }
    val recorder = remember { AudioRecorder(context, vm.repo.mediaDir) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var micDenied by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val focus = remember { FocusRequester() }

    fun persist() = Draft.save(context, text, attachments)
    fun close() {
        if (recording) recorder.stop()?.let { attachments += Media.fromFile(it, "audio/mp4") }
        persist()
        vm.captureOpen = false
    }
    fun newFile(ext: String): Pair<File, Uri> {
        val f = File(vm.repo.mediaDir, "${UUID.randomUUID()}.$ext")
        return f to FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingFile?.let { if (ok && it.length() > 0) attachments += Media.fromFile(it, "image/jpeg") else it.delete() }
        pendingFile = null; persist()
    }
    val takeVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        pendingFile?.let { if (ok && it.length() > 0) attachments += Media.fromFile(it, "video/mp4") else it.delete() }
        pendingFile = null; persist()
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.mapNotNull { Media.copyIn(context, it, vm.repo.mediaDir) }.forEach { attachments += it }
        persist()
    }
    fun startRecording() {
        runCatching { recorder.start(); recording = true; elapsed = 0 }.onFailure { micDenied = true }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else micDenied = true
    }

    LaunchedEffect(recording) { while (recording) { elapsed = (System.currentTimeMillis() - recorder.startedAt) / 1000; delay(250) } }
    LaunchedEffect(mode) { if (mode == Mode.TEXT || mode == Mode.LIST) runCatching { focus.requestFocus() } }

    val canSave = text.isNotBlank() || attachments.isNotEmpty()
    val kind = when {
        attachments.isEmpty() -> if (mode == Mode.LIST) CaptureKind.LIST else CaptureKind.TEXT
        attachments.all { it.mimeType.startsWith("audio") } -> CaptureKind.AUDIO
        attachments.all { it.mimeType.startsWith("image") } -> CaptureKind.IMAGE
        attachments.all { it.mimeType.startsWith("video") } -> CaptureKind.VIDEO
        else -> CaptureKind.FILE
    }

    ModalBottomSheet(onDismissRequest = ::close, sheetState = sheet, containerColor = C.canvas, dragHandle = {
        Box(Modifier.padding(top = 12.dp).size(40.dp, 4.dp).clip(R12).background(C.border))
    }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("captureSomething"), style = T.h2)
                IconBtn(Icons.Outlined.Close, tr("close"), ::close)
            }
            Text(tr("captureSubtitle"), style = T.small)

            val modes = listOf(
                Mode.TEXT to Icons.Outlined.TextFields, Mode.LIST to Icons.Outlined.Checklist, Mode.VOICE to Icons.Outlined.Mic,
                Mode.PHOTO to Icons.Outlined.CameraAlt, Mode.VIDEO to Icons.Outlined.Videocam, Mode.FILE to Icons.Outlined.AttachFile,
            )
            modes.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (m, icon) ->
                        val on = m == mode
                        Row(
                            Modifier.weight(1f).height(48.dp).clip(R12).background(if (on) C.tint else C.surface)
                                .border(1.dp, if (on) C.tint else C.border, R12).clickable { mode = m },
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, null, tint = if (on) C.primary else C.secondary, modifier = Modifier.size(18.dp))
                            Text(" " + tr(m.key), style = T.micro, color = if (on) C.primary else C.secondary)
                        }
                    }
                }
            }

            when (mode) {
                Mode.VOICE -> Btn(
                    if (recording) tr("stopRecord") else tr("record"),
                    {
                        if (recording) {
                            recorder.stop()?.let { attachments += Media.fromFile(it, "audio/mp4") }
                            recording = false; persist()
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                        else askMic.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    style = if (recording) BtnStyle.PRIMARY else BtnStyle.SECONDARY,
                    icon = if (recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                )
                Mode.PHOTO -> Btn(tr("takePhoto"), { val (f, u) = newFile("jpg"); pendingFile = f; takePhoto.launch(u) }, style = BtnStyle.SECONDARY, icon = Icons.Outlined.CameraAlt)
                Mode.VIDEO -> Btn(tr("recordVideo"), { val (f, u) = newFile("mp4"); pendingFile = f; takeVideo.launch(u) }, style = BtnStyle.SECONDARY, icon = Icons.Outlined.Videocam)
                Mode.FILE -> Btn(tr("chooseFile"), { pickFiles.launch(arrayOf("*/*")) }, style = BtnStyle.SECONDARY, icon = Icons.Outlined.AttachFile)
                else -> Unit
            }
            if (recording) Text(tr("recording", num(clock(elapsed))), style = T.micro, color = C.primary)
            if (micDenied) Text(tr("micDenied"), style = T.micro, color = C.warningText)
            if (attachments.isNotEmpty()) Text(tr("attachments", attachments.size) + ": " + attachments.joinToString { attachmentLabel(it, vm.lang) }, style = T.micro, color = C.primary)

            Box(Modifier.fillMaxWidth().heightIn(min = if (mode == Mode.TEXT || mode == Mode.LIST) 172.dp else 72.dp).card(R16).padding(16.dp)) {
                if (text.isEmpty()) Text(
                    when (mode) { Mode.TEXT -> tr("capturePlaceholder"); Mode.LIST -> tr("listPlaceholder"); else -> tr("caption") },
                    style = T.body.copy(color = C.secondary),
                )
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; persist() },
                    textStyle = T.body,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CloudDone, null, tint = C.secondary, modifier = Modifier.size(18.dp))
                Text(tr("worksOffline"), style = T.micro)
            }
            Btn(tr("saveToInbox"), {
                if (recording) { recorder.stop()?.let { attachments += Media.fromFile(it, "audio/mp4") }; recording = false }
                vm.capture(kind, text, attachments.toList()) { Draft.clear(context) }
                vm.captureOpen = false
            }, enabled = canSave)
            Text(tr("captureFooter"), style = T.micro)
        }
    }
}
