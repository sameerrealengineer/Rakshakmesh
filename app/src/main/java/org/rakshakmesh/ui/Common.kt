package org.rakshakmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.rakshakmesh.R
import org.rakshakmesh.core.Compass
import org.rakshakmesh.device.Phone
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun directionLabel(c: Compass): String = stringResource(
    when (c) {
        Compass.N -> R.string.dir_n
        Compass.NE -> R.string.dir_ne
        Compass.E -> R.string.dir_e
        Compass.SE -> R.string.dir_se
        Compass.S -> R.string.dir_s
        Compass.SW -> R.string.dir_sw
        Compass.W -> R.string.dir_w
        Compass.NW -> R.string.dir_nw
    },
)

@Composable
fun ageText(minutes: Long): String = when {
    minutes < 60 -> stringResource(R.string.age_min, minutes.coerceAtLeast(1).toInt())
    minutes < 60 * 48 -> stringResource(R.string.age_h, (minutes / 60).toInt())
    else -> stringResource(R.string.age_d, (minutes / (60 * 24)).toInt())
}

@Composable
fun levelLabel(level: String): String = stringResource(
    when (level.lowercase()) {
        "red" -> R.string.level_red
        "orange" -> R.string.level_orange
        "yellow" -> R.string.level_yellow
        else -> R.string.level_green
    },
)

fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

fun km(d: Double): String = String.format(Locale.US, "%.1f", d)

/** Current UI language: "or", "hi" or "en". */
@Composable
fun currentLang(): String {
    val lang = LocalConfiguration.current.locales[0].language
    return if (lang == "or" || lang == "hi") lang else "en"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
    )
}

/** Big dial button. ACTION_DIAL: the user presses call in the dialer (FR-SOS-01). */
@Composable
fun DialButton(number: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondary) {
    val ctx = LocalContext.current
    Button(
        onClick = { Phone.dial(ctx, number) },
        modifier = modifier.heightIn(min = 72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Text(" $number", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun InfoCard(text: String, color: Color = MaterialTheme.colorScheme.surfaceVariant, content: (@Composable () -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            content?.invoke()
        }
    }
}
