package org.rakshakmesh.ui.screens

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.data.ChecklistItem
import org.rakshakmesh.ui.BackBar
import org.rakshakmesh.ui.currentLang
import org.rakshakmesh.ui.theme.SosRed

/** Offline checklist from NDMA/OSDMA guidance (FR-CHK-01..04). Audio plays only if bundled. */
@Composable
fun ChecklistScreen(app: RakshakApp, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by app.db.checklist().observeAll().collectAsStateWithLifecycle(emptyList())
    val lang = currentLang()
    val player = remember { AudioPlayer(ctx) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    fun text(i: ChecklistItem) = when (lang) {
        "or" -> i.textOr
        "hi" -> i.textHi
        else -> i.textEn
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(stringResource(R.string.checklist_title), onBack)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val warnings = items.filter { it.isWarning }
            if (warnings.isNotEmpty()) {
                item { Text(stringResource(R.string.checklist_warnings), style = MaterialTheme.typography.titleMedium, color = SosRed) }
                items(warnings, key = { it.id }) { w ->
                    Card(colors = CardDefaults.cardColors(containerColor = SosRed.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = SosRed)
                            Text(text(w), Modifier.weight(1f).padding(start = 12.dp), fontWeight = FontWeight.SemiBold)
                            AudioButton(player, w.id, lang)
                        }
                    }
                }
            }
            for ((phase, label) in listOf("before" to R.string.phase_before, "during" to R.string.phase_during, "after" to R.string.phase_after)) {
                val list = items.filter { !it.isWarning && it.phase == phase }
                if (list.isEmpty()) continue
                item(key = "h-$phase") { Text(stringResource(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(list, key = { it.id }) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = i.done, onCheckedChange = { c -> scope.launch { app.db.checklist().setDone(i.id, c) } })
                        Text(text(i), Modifier.weight(1f))
                        AudioButton(player, i.id, lang)
                    }
                }
            }
            item {
                Text(stringResource(R.string.checklist_reviewed), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                TextButton(onClick = { scope.launch { app.db.checklist().resetAll() } }) { Text(stringResource(R.string.checklist_reset)) }
            }
        }
    }
}

@Composable
private fun AudioButton(player: AudioPlayer, id: String, lang: String) {
    val path = listOf("mp3", "wav").map { "audio/${id}_$lang.$it" }.firstOrNull(player::exists) ?: return
    IconButton(onClick = { player.play(path) }) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.play_audio))
    }
}

/** Plays pre-recorded clips bundled at build time (Sarvam Bulbul, PRD 7.1 A1). Works offline. */
private class AudioPlayer(private val context: Context) {
    private var mp: MediaPlayer? = null
    private val bundled: Set<String> = runCatching { context.assets.list("audio")?.toSet() }.getOrNull().orEmpty()

    fun exists(path: String) = path.removePrefix("audio/") in bundled

    fun play(path: String) {
        release()
        runCatching {
            context.assets.openFd(path).use { fd ->
                mp = MediaPlayer().apply {
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    prepare()
                    start()
                }
            }
        }
    }

    fun release() {
        mp?.release()
        mp = null
    }
}
