package org.rakshakmesh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HolidayVillage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.core.LocationChooser
import org.rakshakmesh.core.MessageType
import org.rakshakmesh.core.NearestShelters
import org.rakshakmesh.core.SmsBodyBuilder
import org.rakshakmesh.data.OutboxRepo
import org.rakshakmesh.ui.InfoCard
import org.rakshakmesh.ui.theme.SafeGreen
import org.rakshakmesh.ui.theme.SosRed

/**
 * Family check-in by SMS (FR-FAM-01..04). Human-readable text, so family does not need the app.
 * No tracking, no READ_SMS, no "missing" labels.
 */
@Composable
fun FamilyScreen(app: RakshakApp, onNeedHelp: () -> Unit) {
    val scope = rememberCoroutineScope()
    val contacts by app.db.contacts().observeAll().collectAsStateWithLifecycle(emptyList())
    val family = contacts.filter { it.role == "family" }
    var result by remember { mutableStateOf<OutboxRepo.HandOff?>(null) }

    fun send(type: MessageType) = scope.launch {
        val profile = app.db.profile().get()
        val name = profile?.name.orEmpty()
        val now = System.currentTimeMillis()
        var location: org.rakshakmesh.core.SosLocation? = null
        val body = when (type) {
            MessageType.AT_SHELTER -> {
                // Nearest verified shelter from the last known fix; no GPS wait for a check-in.
                val fix = app.location.lastKnown()
                location = fix?.let { LocationChooser.choose(null, it, profile?.landmark.orEmpty(), now) }
                val shelter = fix?.let { f ->
                    NearestShelters.find(f.lat, f.lon, app.db.shelters().all().map { it.toRecord() }, limit = 1, maxKm = 2.0).firstOrNull()
                }
                SmsBodyBuilder.atShelter(name, shelter?.shelter?.name, now)
            }
            else -> SmsBodyBuilder.safe(name, now)
        }
        val saved = app.outbox.save(type, body, family.map { it.phone }, location)
        result = app.outbox.handToSmsApp(saved.id)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.family_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.family_note), style = MaterialTheme.typography.bodyLarge)

        if (family.isEmpty()) {
            InfoCard(stringResource(R.string.family_no_contacts), color = Color(0xFFFFE0B2))
        } else {
            Text(
                stringResource(R.string.family_recipients, family.joinToString(", ") { it.label.ifBlank { it.phone } }),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        BigAction(stringResource(R.string.family_safe), Icons.Filled.CheckCircle, SafeGreen, enabled = family.isNotEmpty()) { send(MessageType.SAFE) }
        BigAction(stringResource(R.string.family_shelter), Icons.Filled.HolidayVillage, MaterialTheme.colorScheme.secondary, enabled = family.isNotEmpty()) { send(MessageType.AT_SHELTER) }
        BigAction(stringResource(R.string.family_help), Icons.Filled.Warning, SosRed, enabled = true, onClick = onNeedHelp)

        result?.let { r ->
            val msg = when (r) {
                OutboxRepo.HandOff.HANDED -> stringResource(R.string.sos_handed)
                OutboxRepo.HandOff.NO_SIGNAL -> stringResource(R.string.sos_no_signal)
                OutboxRepo.HandOff.NO_RECIPIENTS -> stringResource(R.string.family_no_contacts)
                OutboxRepo.HandOff.NO_SMS_APP -> stringResource(R.string.sos_no_sms_app)
            }
            InfoCard(stringResource(R.string.sos_saved) + "\n" + msg)
        }
    }
}

@Composable
private fun BigAction(label: String, icon: ImageVector, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
