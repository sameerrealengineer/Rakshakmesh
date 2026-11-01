package org.rakshakmesh.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.rakshakmesh.R
import org.rakshakmesh.RakshakApp
import org.rakshakmesh.alerts.AlertScheduler
import org.rakshakmesh.core.Districts
import org.rakshakmesh.data.Contact
import org.rakshakmesh.data.Profile
import org.rakshakmesh.ui.currentLang

private val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

/**
 * Onboarding (FR-ALR-01, FR-SOS-05, FR-SOS-08): language, place, contacts, optional SOS details.
 * Only the district is required. Nothing leaves the phone.
 */
@Composable
fun OnboardingScreen(app: RakshakApp, isEdit: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = currentLang()

    var name by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var block by remember { mutableStateOf("") }
    var village by remember { mutableStateOf("") }
    var landmark by remember { mutableStateOf("") }
    val family = remember { mutableStateListOf("", "", "", "", "") }
    val familyLabels = remember { mutableStateListOf("", "", "", "", "") }
    var volunteer by remember { mutableStateOf("") }
    var blood by remember { mutableStateOf<String?>(null) }
    var cannotWalk by remember { mutableStateOf(false) }
    var pregnant by remember { mutableStateOf(false) }
    var infant by remember { mutableStateOf(false) }
    var oldDistrict by remember { mutableStateOf("") }
    var showDistricts by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        app.db.profile().get()?.let { p ->
            name = p.name; district = p.district; oldDistrict = p.district; block = p.block
            village = p.village; landmark = p.landmark; blood = p.bloodGroup
            cannotWalk = p.cannotWalk; pregnant = p.pregnant; infant = p.infant
        }
        val contacts = app.db.contacts().all()
        contacts.filter { it.role == "family" }.take(5).forEachIndexed { i, c -> family[i] = c.phone; familyLabels[i] = c.label }
        contacts.firstOrNull { it.role == "volunteer" }?.let { volunteer = it.phone }
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) scope.launch {
            val fix = app.location.lastKnown() ?: app.location.currentFix()
            fix?.let { district = Districts.suggest(it.lat, it.lon) }
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isEdit) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.onb_intro), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.onb_language), style = MaterialTheme.typography.titleMedium)
            LanguagePicker()
            HorizontalDivider()
        }

        Text(stringResource(R.string.onb_place), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { showDistricts = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (district.isBlank()) stringResource(R.string.select_district) else stringResource(R.string.onb_district) + ": " + district)
        }
        TextButton(onClick = { perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
            Text(stringResource(R.string.onb_use_gps))
        }
        if (error) Text(stringResource(R.string.onb_need_district), color = MaterialTheme.colorScheme.error)
        Field(block, { block = it }, R.string.onb_block)
        Field(village, { village = it }, R.string.onb_village)
        Field(landmark, { landmark = it }, R.string.onb_landmark, hint = stringResource(R.string.onb_landmark_hint))
        Field(name, { name = it }, R.string.onb_name)
        HorizontalDivider()

        Text(stringResource(R.string.onb_contacts), style = MaterialTheme.typography.titleMedium)
        for (i in 0 until 5) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    familyLabels[i], { familyLabels[i] = it }, singleLine = true,
                    label = { Text(stringResource(R.string.onb_contact_label)) }, modifier = Modifier.weight(0.4f),
                )
                OutlinedTextField(
                    family[i], { family[i] = it.filter { c -> c.isDigit() || c == '+' } }, singleLine = true,
                    label = { Text(stringResource(R.string.onb_contact_n, i + 1)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(0.6f),
                )
            }
        }
        OutlinedTextField(
            volunteer, { volunteer = it.filter { c -> c.isDigit() || c == '+' } }, singleLine = true,
            label = { Text(stringResource(R.string.onb_volunteer)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()

        Text(stringResource(R.string.onb_optional), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.onb_blood))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            BLOOD_GROUPS.take(4).forEach { g -> BloodChip(g, blood == g, Modifier.weight(1f)) { blood = if (blood == g) null else g } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            BLOOD_GROUPS.drop(4).forEach { g -> BloodChip(g, blood == g, Modifier.weight(1f)) { blood = if (blood == g) null else g } }
        }
        Check(cannotWalk, { cannotWalk = it }, R.string.onb_cannot_walk)
        Check(pregnant, { pregnant = it }, R.string.onb_pregnant)
        Check(infant, { infant = it }, R.string.onb_infant)

        Button(
            onClick = {
                if (district.isBlank()) { error = true; return@Button }
                scope.launch {
                    app.db.profile().save(
                        Profile(
                            name = name.trim(), village = village.trim(), block = block.trim(), district = district,
                            landmark = landmark.trim(), bloodGroup = blood, cannotWalk = cannotWalk,
                            pregnant = pregnant, infant = infant, language = lang,
                        ),
                    )
                    val contacts = family.indices.filter { family[it].isNotBlank() }
                        .map { Contact(label = familyLabels[it].trim(), phone = family[it], role = "family") } +
                        listOfNotNull(volunteer.takeIf { it.isNotBlank() }?.let { Contact(label = "Volunteer", phone = it, role = "volunteer") })
                    app.db.contacts().replaceAll(contacts)
                    if (district != oldDistrict) {
                        app.settings.resetFetch()
                        app.db.alerts().deleteDistrict(oldDistrict)
                    }
                    app.settings.setOnboarded(true)
                    if (!app.settings.get().cycloneMode) {
                        AlertScheduler.schedule(ctx)
                        AlertScheduler.refreshNow(ctx)
                    }
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(if (isEdit) R.string.save else R.string.finish)) }
    }

    if (showDistricts) {
        AlertDialog(
            onDismissRequest = { showDistricts = false },
            title = { Text(stringResource(R.string.select_district)) },
            text = {
                LazyColumn {
                    items(Districts.ALL) { d ->
                        Text(
                            d,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { district = d; error = false; showDistricts = false }.padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDistricts = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int, hint: String? = null) {
    OutlinedTextField(
        value, onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        placeholder = if (hint != null) { { Text(hint) } } else null,
    )
}

@Composable
private fun Check(value: Boolean, onChange: (Boolean) -> Unit, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!value) }) {
        Checkbox(value, onChange)
        Text(stringResource(label))
    }
}

@Composable
private fun BloodChip(g: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick, modifier) { Text(g) } else OutlinedButton(onClick, modifier) { Text(g) }
}
