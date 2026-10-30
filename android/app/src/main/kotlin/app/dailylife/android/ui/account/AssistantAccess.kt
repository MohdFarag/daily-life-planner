package app.dailylife.android.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.dailylife.android.sync.Account
import app.dailylife.android.ui.components.AppButton
import app.dailylife.android.ui.components.ButtonStyle
import app.dailylife.android.ui.components.Field
import app.dailylife.android.ui.components.SectionLabel
import app.dailylife.android.ui.components.card
import app.dailylife.android.ui.format.shortDate
import app.dailylife.android.ui.i18n.LocalLang
import app.dailylife.android.ui.i18n.tr
import app.dailylife.android.ui.theme.AppType
import app.dailylife.android.viewmodel.AppViewModel
import java.time.Instant
import java.time.ZoneId

/**
 * Lets the user connect an AI assistant over MCP with its own revocable token (MCP03): never the
 * app's session or a Google token.
 */
@Composable
fun AssistantAccess(vm: AppViewModel, account: Account) {
    val controller = vm.account
    LaunchedEffect(account.userId) { controller.loadAssistantTokens() }
    var name by rememberSaveable { mutableStateOf("") }

    SectionLabel(tr("assistantAccess"))
    Text(tr("assistantAccessExplain"), style = AppType.small)
    controller.newAssistantToken?.let { NewToken(it, account.serverUrl.trimEnd('/') + "/v1/mcp") }
    controller.assistantTokens.forEach { token ->
        Row(
            Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(token.name, style = AppType.label)
                Text(tr("expiresOn", isoDate(token.expiresAt)), style = AppType.micro)
            }
            AppButton(
                tr("revoke"),
                { controller.revokeAssistantToken(token.id) },
                style = ButtonStyle.QUIET,
            )
        }
    }
    Field(name, tr("assistantName")) { name = it }
    AppButton(
        tr("createToken"),
        {
            controller.createAssistantToken(name)
            name = ""
        },
        enabled = name.isNotBlank() && !controller.busy,
        style = ButtonStyle.SECONDARY,
    )
}

@Composable
private fun NewToken(token: String, endpoint: String) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxWidth().card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tr("tokenShownOnce"), style = AppType.label)
        SelectionContainer { Text(token, style = AppType.small) }
        Text(tr("mcpEndpoint", endpoint), style = AppType.micro)
        AppButton(tr("copyToken"), { clipboard.setText(AnnotatedString(token)) })
    }
}

@Composable
private fun isoDate(instant: String): String {
    val lang = LocalLang.current
    val date = runCatching {
        Instant.parse(instant).atZone(ZoneId.systemDefault()).toLocalDate()
    }
        .getOrNull()
    return date?.let { shortDate(it, lang) } ?: instant
}
