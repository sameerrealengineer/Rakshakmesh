package org.rakshakmesh.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch
import org.rakshakmesh.BuildConfig
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.alerts.AlertScheduler
import org.rakshakmesh.data.AppSettings
import org.rakshakmesh.ui.BackBar

fun setAppLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}

@Composable
fun LanguagePicker() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("or" to R.string.lang_or, "hi" to R.string.lang_hi, "en" to R.string.lang_en).forEach { (tag, label) ->
            OutlinedButton(onClick = { setAppLanguage(tag) }, modifier = Modifier.weight(1f)) { Text(stringResource(label)) }
        }
    }
}

@Composable
fun SettingsScreen(app: RakshakApp, settings: AppSettings, onBack: () -> Unit, onEditProfile: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var relay by remember { mutableStateOf(settings.relayUrl) }
    var mapUrl by remember { mutableStateOf(settings.mapUrl) }

    Column(Modifier.fillMaxSize()) {
        BackBar(stringResource(R.string.settings_title), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            LanguagePicker()
            HorizontalDivider()

            // Cyclone mode (FR-BAT-01).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_cyclone), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_cyclone_desc), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = settings.cycloneMode, onCheckedChange = { on ->
                    scope.launch {
                        app.settings.setCycloneMode(on)
                        if (on) AlertScheduler.cancelAll(ctx) else AlertScheduler.schedule(ctx)
                    }
                })
            }
            HorizontalDivider()

            Button(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_profile)) }
            HorizontalDivider()

            OutlinedTextField(relay, { relay = it }, label = { Text(stringResource(R.string.settings_relay)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(mapUrl, { mapUrl = it }, label = { Text(stringResource(R.string.settings_map_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                scope.launch {
                    app.settings.setRelayUrl(relay)
                    app.settings.setMapUrl(mapUrl)
                }
            }) { Text(stringResource(R.string.save)) }
            HorizontalDivider()

            Text(stringResource(R.string.settings_about, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.settings_data_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
