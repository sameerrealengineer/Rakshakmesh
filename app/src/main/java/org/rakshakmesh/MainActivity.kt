package org.rakshakmesh

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.rakshakmesh.data.AppSettings
import org.rakshakmesh.ui.RakshakRoot
import org.rakshakmesh.ui.theme.RakshakTheme

// AppCompatActivity so the per-app language (Odia/Hindi/English) works on Android 8-12 too.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RakshakApp
        setContent {
            val settings by app.settings.flow.collectAsStateWithLifecycle(initialValue = null as AppSettings?)
            RakshakTheme(cycloneMode = settings?.cycloneMode == true) {
                settings?.let { RakshakRoot(app, it) }
            }
        }
    }
}
