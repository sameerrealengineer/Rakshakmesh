package org.rakshakmesh

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.rakshakmesh.mesh.Permissions
import org.rakshakmesh.ui.screens.MapAssets

/**
 * End-to-end smoke test on an emulator: first-run setup, every tab, SOS, a nearby (mesh)
 * message, the offline map, the places list and the alerts screen. No family numbers are
 * entered so the phone's SMS app is never opened during the test.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    private val permissions = GrantPermissionRule.grant(*Permissions.all())
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissions).around(compose)

    private val app get() = ApplicationProvider.getApplicationContext<RakshakApp>()
    private fun s(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    @Test
    fun bundledDataIsPresent() {
        assertTrue("offline map must be bundled in the APK", MapAssets.bundled(app))
        assertTrue("map fonts must be bundled", MapAssets.hasGlyphs(app))
        compose.waitUntil(30_000) { runBlocking { app.db.shelters().count() } > 0 }
        val rows = runBlocking { app.db.shelters().all() }
        assertTrue("expected OSM places", rows.size > 50)
        assertTrue("expected cyclone shelters", rows.any { it.type == "cyclone_shelter" })
        assertTrue("expected hospitals", rows.any { it.type == "hospital" })
    }

    @Test
    fun fullUserFlow() {
        onboardIfNeeded()

        // Home
        compose.onNodeWithTag("sos_button").assertExists()
        compose.onNodeWithText(s(R.string.call_112)).assertExists()

        // Nearby: send a message over the mesh (stored even if no phone is in range).
        compose.onNodeWithTag("tab_nearby").performClick()
        compose.onNodeWithText(s(R.string.nearby_title)).assertExists()
        compose.onNodeWithTag("mesh_input").performTextInput("hello from test")
        compose.onNodeWithTag("mesh_send").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("hello from test").fetchSemanticsNodes().isNotEmpty() }
        val stored = runBlocking { app.db.mesh().syncable(System.currentTimeMillis()) }
        assertTrue(stored.any { it.body == "hello from test" && it.outgoing })

        // Map: the offline map view appears, and the list view opens.
        compose.onNodeWithTag("tab_map").performClick()
        compose.waitUntil(90_000) { compose.onAllNodes(hasTestTag("map_view")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("map_list_toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("map_list_toggle").performClick()

        // Alerts: never a raw HTTP error; the offline guide is always there.
        compose.onNodeWithTag("tab_alerts").performClick()
        compose.onNodeWithText(s(R.string.guide_title)).performScrollTo().assertExists()
        assertEquals(0, compose.onAllNodes(hasText("404", substring = true)).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("HTTP", substring = true)).fetchSemanticsNodes().size)

        // Family: "I am safe" with no numbers → saved + shared over mesh, no SMS app opened.
        compose.onNodeWithTag("tab_family").performClick()
        compose.onNodeWithText(s(R.string.family_safe)).performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasText(s(R.string.sos_saved), substring = true)).fetchSemanticsNodes().isNotEmpty() }

        // SOS: three taps, saved locally even with no GPS fix and no numbers.
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithTag("sos_button").performClick()
        compose.onNodeWithText(s(R.string.sos_trapped)).performClick()
        compose.onNodeWithTag("sos_send").performScrollTo().performClick()
        compose.waitUntil(45_000) { compose.onAllNodes(hasText(s(R.string.sos_saved), substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val outbox = runBlocking { app.db.outbox().unsent() }
        assertTrue("SOS must be in the outbox", outbox.any { it.type == "SOS" && it.body.startsWith("SOS RM1 TRAPPED-WATER") })
        val meshSos = runBlocking { app.db.mesh().syncable(System.currentTimeMillis()) }
        assertTrue("SOS must also go to the mesh", meshSos.any { it.type == "SOS" && it.outgoing })
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

        // Settings
        compose.waitForIdle()
        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithTag("mesh_switch").assertExists()
        compose.onNodeWithTag("cyclone_switch").assertExists()
    }

    private fun onboardIfNeeded() {
        compose.waitForIdle()
        if (compose.onAllNodes(hasTestTag("onb_next")).fetchSemanticsNodes().isEmpty()) return
        compose.onNodeWithTag("onb_next").performClick() // language → place
        compose.onNodeWithTag("pick_district").performClick()
        compose.onNodeWithTag("district_Puri").performScrollTo().performClick()
        compose.onNodeWithTag("name_field").performScrollTo().performTextInput("Tester")
        compose.onNodeWithTag("onb_next").performClick() // place → contacts
        compose.onNodeWithTag("onb_next").performClick() // contacts → health
        compose.onNodeWithTag("onb_next").performClick() // health → permissions
        compose.onNodeWithTag("onb_next").performClick() // allow and start
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("sos_button")).fetchSemanticsNodes().isNotEmpty() }
    }
}
