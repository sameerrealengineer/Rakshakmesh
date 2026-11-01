package org.rakshakmesh

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.rakshakmesh.data.AppSettings
import org.rakshakmesh.mesh.MeshService
import org.rakshakmesh.mesh.Notifications
import org.rakshakmesh.ui.DeepLink
import org.rakshakmesh.ui.RakshakRoot
import org.rakshakmesh.ui.theme.RakshakTheme

// AppCompatActivity so the per-app language (Odia/Hindi/English) works on Android 8-12 too.
class MainActivity : AppCompatActivity() {
    private val deepLink = MutableStateFlow<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        val app = application as RakshakApp
        setContent {
            val settings by app.settings.flow.collectAsStateWithLifecycle(initialValue = null as AppSettings?)
            val link by deepLink.collectAsStateWithLifecycle()
            RakshakTheme(cycloneMode = settings?.cycloneMode == true) {
                settings?.let { RakshakRoot(app, it, link, onLinkHandled = { deepLink.value = null }) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        val app = application as RakshakApp
        lifecycleScope.launch {
            val s = app.settings.get()
            if (s.onboarded && s.meshEnabled) MeshService.start(this@MainActivity)
        }
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val route = intent.getStringExtra(Notifications.EXTRA_ROUTE) ?: return
        deepLink.value = DeepLink(route, intent.getStringExtra(Notifications.EXTRA_FORWARD_SOS))
    }
}
