package org.rakshakmesh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.core.OutboxState
import org.rakshakmesh.ui.BackBar
import org.rakshakmesh.ui.InfoCard
import org.rakshakmesh.ui.formatDateTime

@Composable
fun stateLabel(state: OutboxState): String = stringResource(
    when (state) {
        OutboxState.SAVED -> R.string.state_saved
        OutboxState.HANDED_TO_SMS_APP -> R.string.state_handed
        OutboxState.SENT -> R.string.state_sent
        OutboxState.DELIVERED -> R.string.state_delivered
        OutboxState.FAILED -> R.string.state_failed
    },
)

/** Every saved message with its exact state. Nothing is deleted automatically. */
@Composable
fun OutboxScreen(app: RakshakApp, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val items by app.db.outbox().observeAll().collectAsStateWithLifecycle(emptyList())
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackBar(stringResource(R.string.outbox_title), onBack)
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { InfoCard(stringResource(R.string.outbox_handed_note)) }
            if (items.isEmpty()) item { Text(stringResource(R.string.outbox_empty), Modifier.padding(16.dp)) }
            items(items, key = { it.id }) { m ->
                val state = OutboxState.valueOf(m.state)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stateLabel(state),
                            fontWeight = FontWeight.Bold,
                            color = if (state.notSentYet) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(formatDateTime(m.createdAt), style = MaterialTheme.typography.bodyMedium)
                        Text(m.body, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                        Text(m.recipients.replace(";", ", "), style = MaterialTheme.typography.bodyMedium)
                        if (state != OutboxState.DELIVERED && state != OutboxState.SENT) {
                            OutlinedButton(onClick = { scope.launch { app.outbox.handToSmsApp(m.id) } }) {
                                Text(stringResource(R.string.outbox_send_again))
                            }
                        }
                    }
                }
            }
            if (items.isNotEmpty()) item {
                TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.outbox_clear)) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.outbox_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { app.db.outbox().clearAll() }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
