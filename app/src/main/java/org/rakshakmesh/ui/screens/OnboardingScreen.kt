package org.rakshakmesh.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import org.rakshakmesh.mesh.MeshService
import org.rakshakmesh.mesh.Permissions
import org.rakshakmesh.ui.BackBar
import org.rakshakmesh.ui.currentLang

private val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

/**
 * First-run setup as a short wizard: language → place → family numbers → optional health
 * details → permissions. Only the district is required. Also used to edit details later.
 */
@Composable
fun OnboardingScreen(app: RakshakApp, isEdit: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = currentLang()
    val firstStep = if (isEdit) 1 else 0
    val lastStep = if (isEdit) 3 else 4
    var step by remember { mutableIntStateOf(firstStep) }

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

    val gpsPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) scope.launch {
            val fix = app.location.lastKnown() ?: app.location.currentFix()
            fix?.let { district = Districts.suggest(it.lat, it.lon); error = false }
        }
    }

    suspend fun save() {
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
            oldDistrict = district
        }
    }

    fun finish() = scope.launch {
        save()
        if (!isEdit) {
            app.settings.setOnboarded(true)
            if (Permissions.hasMesh(ctx)) {
                app.settings.setMeshEnabled(true)
                MeshService.start(ctx)
            }
        }
        if (!app.settings.get().cycloneMode) {
            AlertScheduler.schedule(ctx)
            AlertScheduler.refreshNow(ctx)
        }
        onDone()
    }

    val allPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { finish() }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (isEdit) BackBar(stringResource(R.string.settings_profile), onDone) else Spacer(Modifier.statusBarsPadding())
        // Progress dots.
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            for (i in firstStep..lastStep) {
                Box(
                    Modifier.padding(4.dp).size(if (i == step) 12.dp else 8.dp)
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                0 -> {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.onb_intro), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.onb_language), style = MaterialTheme.typography.titleMedium)
                    LanguagePicker()
                }
                1 -> {
                    Text(stringResource(R.string.onb_place), style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(
                        onClick = { showDistricts = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("pick_district"),
                    ) {
                        Text(if (district.isBlank()) stringResource(R.string.select_district) else stringResource(R.string.onb_district) + ": " + district)
                    }
                    TextButton(onClick = {
                        gpsPerm.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text(stringResource(R.string.onb_use_gps)) }
                    if (error) Text(stringResource(R.string.onb_need_district), color = MaterialTheme.colorScheme.error)
                    Field(block, { block = it }, R.string.onb_block)
                    Field(village, { village = it }, R.string.onb_village)
                    Field(landmark, { landmark = it }, R.string.onb_landmark, hint = stringResource(R.string.onb_landmark_hint))
                    Field(name, { name = it }, R.string.onb_name, tag = "name_field")
                }
                2 -> {
                    Text(stringResource(R.string.onb_contacts), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.onb_contacts_why), style = MaterialTheme.typography.bodyMedium)
                    for (i in 0 until 5) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                familyLabels[i], { familyLabels[i] = it }, singleLine = true,
                                label = { Text(stringResource(R.string.onb_contact_label)) }, modifier = Modifier.weight(0.4f),
                            )
                            OutlinedTextField(
                                family[i], { family[i] = it.filter { c -> c.isDigit() || c == '+' } }, singleLine = true,
                                label = { Text(stringResource(R.string.onb_contact_n, i + 1)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.weight(0.6f).testTag("phone_$i"),
                            )
                        }
                    }
                    OutlinedTextField(
                        volunteer, { volunteer = it.filter { c -> c.isDigit() || c == '+' } }, singleLine = true,
                        label = { Text(stringResource(R.string.onb_volunteer)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                    )
                }
                3 -> {
                    Text(stringResource(R.string.onb_optional), style = MaterialTheme.typography.titleLarge)
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
                }
                else -> {
                    Text(stringResource(R.string.onb_perm_title), style = MaterialTheme.typography.titleLarge)
                    PermRow(Icons.Filled.WifiTethering, R.string.onb_perm_mesh)
                    PermRow(Icons.Filled.LocationOn, R.string.onb_perm_location)
                    PermRow(Icons.Filled.Notifications, R.string.onb_perm_notif)
                    PermRow(Icons.Filled.Mic, R.string.onb_perm_mic)
                    Text(stringResource(R.string.onb_perm_privacy), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        // Navigation buttons.
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > firstStep) {
                OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.back)) }
            }
            Button(
                onClick = {
                    when {
                        step == 1 && district.isBlank() -> error = true
                        step < lastStep -> step++
                        isEdit -> finish()
                        else -> allPerms.launch(Permissions.all())
                    }
                },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("onb_next"),
            ) {
                Text(
                    stringResource(
                        when {
                            step < lastStep -> R.string.next
                            isEdit -> R.string.save
                            else -> R.string.onb_allow_finish
                        },
                    ),
                )
            }
        }
        if (step == 4 && !isEdit) {
            TextButton(onClick = { finish() }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)) {
                Text(stringResource(R.string.onb_skip_perms))
            }
        }
    }

    if (showDistricts) {
        AlertDialog(
            onDismissRequest = { showDistricts = false },
            title = { Text(stringResource(R.string.select_district)) },
            text = {
                LazyColumn {
                    items(Districts.ALL) { d ->
                        Text(
                            d, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { district = d; error = false; showDistricts = false }
                                .padding(12.dp).testTag("district_$d"),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDistricts = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PermRow(icon: ImageVector, text: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int, hint: String? = null, tag: String? = null) {
    OutlinedTextField(
        value, onChange, singleLine = true,
        modifier = Modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it },
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
