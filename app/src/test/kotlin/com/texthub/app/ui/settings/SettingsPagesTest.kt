package com.texthub.app.ui.settings

import com.texthub.core.prefs.PrefsData
import com.texthub.core.prefs.PrefsKeys
import com.texthub.core.prefs.UiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Settings information architecture after the simplification round: every stored preference
 * is still reachable, nothing is nested deeper than two levels, shared keys are the two documented
 * pairs, the reset actions keep their placement and confirmation count, and back navigation walks
 * up the parent chain and then out.
 */
class SettingsPagesTest {

    // ------------------------------------------------------------------ structure

    @Test fun rootListsExactlyTheSixDestinationsInOrder() {
        assertEquals(
            listOf(
                SettingsPage.APPEARANCE, SettingsPage.ACCESSIBILITY, SettingsPage.PRIVACY,
                SettingsPage.DATA, SettingsPage.ADVANCED, SettingsPage.ABOUT,
            ),
            SettingsPage.TOP_LEVEL,
        )
        SettingsPage.TOP_LEVEL.forEach { assertEquals(SettingsPage.ROOT, it.parent) }
    }

    @Test fun nothingIsNestedDeeperThanTwoLevels() {
        SettingsPage.values().forEach { assertTrue("${it.name} is too deep", it.depth <= 2) }
        assertEquals(listOf(SettingsPage.ROOT, SettingsPage.APPEARANCE, SettingsPage.INTERFACE), SettingsPage.INTERFACE.path)
        assertEquals(listOf(SettingsPage.ROOT, SettingsPage.ADVANCED, SettingsPage.DIAGNOSTICS), SettingsPage.DIAGNOSTICS.path)
    }

    @Test fun everyPageExceptRootAndAboutHasAtLeastOneRowOrAction() {
        SettingsPage.values().filter { it != SettingsPage.ROOT && it != SettingsPage.ABOUT }.forEach { page ->
            val rows = SettingRow.values().count { it.page == page }
            val actions = SettingsAction.values().count { it.page == page }
            assertTrue("${page.name} would be an empty page", rows + actions > 0)
        }
    }

    @Test fun subPagesAreOnlyUsedWhereTheyReduceClutter() {
        // A second-level page must carry more than two controls; otherwise it is over-nesting.
        listOf(SettingsPage.INTERFACE, SettingsPage.DIAGNOSTICS).forEach { page ->
            val count = SettingRow.values().count { it.page == page } + SettingsAction.values().count { it.page == page }
            assertTrue("${page.name} has only $count controls", count >= 3)
        }
    }

    // --------------------------------------------------------------- reachability

    @Test fun everyStoredPreferenceIsReachableFromSomeRow() {
        val reachable = SettingRow.values().map { it.prefKey }.toSet()
        SettingRow.REQUIRED_KEYS.forEach { key -> assertTrue("$key is not reachable in Settings", key in reachable) }
        assertEquals(18 + 5, SettingRow.REQUIRED_KEYS.size)
    }

    @Test fun rowsThatShareAKeyAreExactlyTheDocumentedPairs() {
        val byKey = SettingRow.values().groupBy { it.prefKey }
        val shared = byKey.values.filter { it.size > 1 }.map { it.toSet() }.toSet()
        assertEquals(SettingRow.SHARED_KEY_PAIRS, shared)
        byKey.values.forEach { assertTrue(it.size <= 2) }
        // A shared pair is one appearance control plus one accessibility control, never two on one page.
        SettingRow.SHARED_KEY_PAIRS.forEach { pair ->
            assertEquals(2, pair.map { it.page }.toSet().size)
            assertTrue(pair.any { it.page == SettingsPage.ACCESSIBILITY })
        }
    }

    @Test fun placementFollowsTheSpecification() {
        fun on(page: SettingsPage) = SettingRow.values().filter { it.page == page }.toSet()
        assertEquals(setOf(SettingRow.THEME, SettingRow.ACCENT, SettingRow.DYNAMIC_COLOR, SettingRow.TEXT_SIZE), on(SettingsPage.APPEARANCE))
        assertEquals(setOf(SettingRow.LAYOUT_DENSITY, SettingRow.SHOW_TOOL_ICONS, SettingRow.MONOSPACE_OUTPUT, SettingRow.UI_ANIMATION), on(SettingsPage.INTERFACE))
        assertEquals(
            setOf(SettingRow.LARGE_TEXT, SettingRow.REDUCE_ANIMATIONS, SettingRow.HIGH_CONTRAST, SettingRow.ICON_LABELS, SettingRow.LARGE_TOUCH_TARGETS, SettingRow.HAPTICS),
            on(SettingsPage.ACCESSIBILITY),
        )
        assertEquals(
            setOf(SettingRow.CLEAR_ON_TOOL_SWITCH, SettingRow.CLEAR_ON_BACKGROUND, SettingRow.SENSITIVE_WARNINGS, SettingRow.CONFIRM_PRIVATE_KEY_COPY, SettingRow.HIDE_PRIVATE_KEY_PREVIEW),
            on(SettingsPage.PRIVACY),
        )
        assertEquals(setOf(SettingRow.SHOW_TOOL_ID, SettingRow.SHOW_DETECTION_DETAILS, SettingRow.SHOW_VALIDATION_DETAILS), on(SettingsPage.DIAGNOSTICS))
        assertTrue(on(SettingsPage.DATA).isEmpty())
        assertTrue(on(SettingsPage.ABOUT).isEmpty())
        assertTrue(on(SettingsPage.ROOT).isEmpty())
        // Diagnostics never appear on the root or directly on Advanced.
        assertFalse(on(SettingsPage.ADVANCED).any { it.prefKey in setOf(PrefsKeys.SHOW_TOOL_ID, PrefsKeys.SHOW_DETECTION_DETAILS, PrefsKeys.SHOW_VALIDATION_DETAILS) })
    }

    @Test fun keysAndDefaultsAreUnchangedByTheRestructure() {
        // The rows point at the same keys 1.6.7 stored, so saved settings carry over unchanged.
        val stored = PrefsData().withUiSettings(UiSettings(largeText = true, showToolId = true, hidePrivateKeyPreview = false))
        val reread = stored.uiSettings()
        assertTrue(reread.largeText); assertTrue(reread.showToolId); assertFalse(reread.hidePrivateKeyPreview)
        assertEquals("large_text", SettingRow.TEXT_SIZE.prefKey)
        assertEquals("ui_animation", SettingRow.REDUCE_ANIMATIONS.prefKey)
        assertEquals(PrefsKeys.AUTO_PROCESS, SettingRow.AUTO_PROCESS.prefKey)
        assertEquals(UiSettings.DEFAULT, PrefsData().uiSettings())
    }

    // -------------------------------------------------------------------- actions

    @Test fun resetActionsKeepTheirGroupingAndConfirmations() {
        val data = SettingsAction.values().filter { it.page == SettingsPage.DATA }
        assertEquals(
            listOf(
                SettingsAction.RESTORE_PREFERENCES, SettingsAction.RESET_TOOL_SETTINGS, SettingsAction.RESET_FAVORITES,
                SettingsAction.CLEAR_SAVED_RSA_KEYS, SettingsAction.CLEAR_EVERYTHING,
            ),
            data,
        )
        // Normal resets first and non-destructive; vault-touching actions last and destructive.
        assertEquals(listOf(false, false, false, true, true), data.map { it.destructive })
        assertEquals(2, SettingsAction.CLEAR_EVERYTHING.confirmations)
        SettingsAction.values().forEach { assertTrue(it.confirmations >= 1) }
        assertEquals(SettingsPage.DIAGNOSTICS, SettingsAction.RESET_TOOL_SETTINGS_FROM_DIAGNOSTICS.page)
        assertFalse(SettingsAction.RESET_TOOL_SETTINGS_FROM_DIAGNOSTICS.destructive)
    }

    // ----------------------------------------------------------------- navigation

    @Test fun backWalksUpTheParentsAndThenLeaves() {
        var nav = SettingsNavigation()
        assertTrue(nav.atRoot)
        nav = nav.open(SettingsPage.APPEARANCE).open(SettingsPage.INTERFACE)
        assertEquals(SettingsPage.INTERFACE, nav.page)
        nav = nav.back()!!
        assertEquals(SettingsPage.APPEARANCE, nav.page)
        nav = nav.back()!!
        assertTrue(nav.atRoot)
        assertNull("back at the root means leaving Settings", nav.back())
    }

    @Test fun openingAlwaysStartsAtTheRoot() {
        assertEquals(SettingsPage.ROOT, SettingsNavigation().page)
        SettingsPage.values().forEach { page ->
            var nav = SettingsNavigation().open(page)
            var steps = 0
            while (!nav.atRoot) { nav = nav.back()!!; steps++ }
            assertEquals(page.depth, steps)
        }
    }
}
