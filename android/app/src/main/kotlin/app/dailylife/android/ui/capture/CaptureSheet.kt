package app.dailylife.android.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.dailylife.android.domain.CaptureInput
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.AppIconButton
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.Corner12
import app.dailylife.android.ui.components.Corner16
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.format.attachmentLabel
import app.dailylife.android.ui.format.clock
import app.dailylife.android.ui.i18n.num
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppColors
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import kotlinx.coroutines.delay

/** Quick capture (CAP01–CAP03): text, list, voice, photo, video or files, saved to the inbox. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(vm: AppViewModel) {
    val state = rememberCaptureSheetState(vm.mediaDir)

    fun close() {
        state.stopRecording()
        state.saveDraft()
        vm.captureOpen = false
    }

    fun save() {
        state.stopRecording()
        val input =
            CaptureInput(
                captureKind(state.mode, state.attachments),
                state.text,
                state.attachments.toList(),
            )
        vm.capture.save(input) { state.clearDraft() }
        vm.captureOpen = false
    }

    ModalBottomSheet(
        onDismissRequest = ::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.canvas,
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp)
                    .size(40.dp, 4.dp)
                    .clip(Corner12)
                    .background(AppColors.border)
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(onClose = ::close)
            ModePicker(state.mode) { state.mode = it }
            ModeAction(state)
            Status(state, vm.lang)
            TextArea(state)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.CloudDone,
                    null,
                    tint = AppColors.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(tr("worksOffline"), style = AppType.micro)
            }
            AppButton(tr("saveToInbox"), ::save, enabled = state.canSave)
            Text(tr("captureFooter"), style = AppType.micro)
        }
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("captureSomething"), style = AppType.h2)
        AppIconButton(Icons.Outlined.Close, tr("close"), onClose)
    }
    Text(tr("captureSubtitle"), style = AppType.small)
}

@Composable
private fun ModePicker(selected: CaptureMode, onSelect: (CaptureMode) -> Unit) {
    CaptureMode.entries.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { mode ->
                val on = mode == selected
                val color = if (on) AppColors.primary else AppColors.secondary
                Row(
                    Modifier.weight(1f)
                        .height(48.dp)
                        .clip(Corner12)
                        .background(if (on) AppColors.tint else AppColors.surface)
                        .border(1.dp, if (on) AppColors.tint else AppColors.border, Corner12)
                        .clickable { onSelect(mode) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(mode.icon(), null, tint = color, modifier = Modifier.size(18.dp))
                    Text(" " + tr(mode.labelKey), style = AppType.micro, color = color)
                }
            }
        }
    }
}

/** The button for the selected media mode: record, take a photo or video, or pick files. */
@Composable
private fun ModeAction(state: CaptureSheetState) {
    val context = LocalContext.current
    val takePhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
            state.onCameraResult(it, "image/jpeg")
        }
    val takeVideo =
        rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) {
            state.onCameraResult(it, "video/mp4")
        }
    val pickFiles =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
            state::onFilesPicked,
        )
    val askMic =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) state.startRecording() else state.onMicPermissionDenied()
        }
    LaunchedEffect(state.recording) {
        while (state.recording) {
            state.refreshRecordingTime()
            delay(250)
        }
    }

    when (state.mode) {
        CaptureMode.VOICE ->
            AppButton(
                if (state.recording) tr("stopRecord") else tr("record"),
                {
                    when {
                        state.recording -> state.stopRecording()
                        hasPermission(context, Manifest.permission.RECORD_AUDIO) ->
                            state.startRecording()
                        else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                style = if (state.recording) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY,
                icon = if (state.recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
            )
        CaptureMode.PHOTO ->
            AppButton(
                tr("takePhoto"),
                { takePhoto.launch(state.newCameraFile("jpg")) },
                style = ButtonStyle.SECONDARY,
                icon = Icons.Outlined.CameraAlt,
            )
        CaptureMode.VIDEO ->
            AppButton(
                tr("recordVideo"),
                { takeVideo.launch(state.newCameraFile("mp4")) },
                style = ButtonStyle.SECONDARY,
                icon = Icons.Outlined.Videocam,
            )
        CaptureMode.FILE ->
            AppButton(
                tr("chooseFile"),
                { pickFiles.launch(arrayOf("*/*")) },
                style = ButtonStyle.SECONDARY,
                icon = Icons.Outlined.AttachFile,
            )
        CaptureMode.TEXT,
        CaptureMode.LIST -> Unit
    }
}

@Composable
private fun Status(state: CaptureSheetState, lang: String) {
    if (state.recording)
        Text(
            tr("recording", num(clock(state.recordedSeconds))),
            style = AppType.micro,
            color = AppColors.primary,
        )
    if (state.micUnavailable)
        Text(tr("micDenied"), style = AppType.micro, color = AppColors.warningText)
    if (state.attachments.isNotEmpty()) {
        val names = state.attachments.joinToString { attachmentLabel(it, lang) }
        Text(
            tr("attachments", state.attachments.size) + ": " + names,
            style = AppType.micro,
            color = AppColors.primary,
        )
    }
}

@Composable
private fun TextArea(state: CaptureSheetState) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.mode) { if (state.mode.isWriting) runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxWidth()
            .heightIn(min = if (state.mode.isWriting) 172.dp else 72.dp)
            .card(Corner16)
            .padding(16.dp)
    ) {
        if (state.text.isEmpty()) {
            val placeholder =
                when (state.mode) {
                    CaptureMode.TEXT -> tr("capturePlaceholder")
                    CaptureMode.LIST -> tr("listPlaceholder")
                    else -> tr("caption")
                }
            Text(placeholder, style = AppType.body.copy(color = AppColors.secondary))
        }
        BasicTextField(
            value = state.text,
            onValueChange = state::updateText,
            textStyle = AppType.body,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

private fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun CaptureMode.icon(): ImageVector =
    when (this) {
        CaptureMode.TEXT -> Icons.Outlined.TextFields
        CaptureMode.LIST -> Icons.Outlined.Checklist
        CaptureMode.VOICE -> Icons.Outlined.Mic
        CaptureMode.PHOTO -> Icons.Outlined.CameraAlt
        CaptureMode.VIDEO -> Icons.Outlined.Videocam
        CaptureMode.FILE -> Icons.Outlined.AttachFile
    }
