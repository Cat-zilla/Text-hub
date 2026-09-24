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
 * The Settings information architecture after the visual hierarchy round: every stored preference
 * is still reachable, pages are flat (no second-level screens at all), every row sits in a labelled
 * group of its own page in the specified order, shared keys are the two documented pairs, the reset
 * actions keep their placement and confirmation count, and back navigation returns to the root and
 * then out.
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

    @Test fun pagesAreFlatWithNoSecondLevelScreens() {
        // The redesign flattened Interface and Diagnostics into their parents; nothing re-introduces them.
        SettingsPage.values().forEach { page ->
            assertTrue("${page.name} must not be deeper than one level", page.depth <= 1)
        }
        SettingsPage.values().filter { it != SettingsPage.ROOT }.forEach { page ->
            assertEquals(SettingsPage.ROOT, page.parent)
        }
    }

    @Test fun everyPageExceptRootAndAboutHasAtLeastOneRowOrAction() {
        SettingsPage.values().filter { it != SettingsPage.ROOT && it != SettingsPage.ABOUT }.forEach { page ->
            val rows = SettingRow.values().count { it.page == page }
            val actions = SettingsAction.values().count { it.page == page }
            assertTrue("${page.name} would be an empty page", rows + actions > 0)
        }
    }

    @Test fun everyGroupHasContentAndBelongsToItsRowsPage() {
        SettingsGroup.values().forEach { group ->
            val rows = SettingRow.of(group).size
            val actions = SettingsAction.of(group).size
            assertTrue("${group.name} would be an empty group", rows + actions > 0)
        }
        SettingRow.values().forEach { row ->
            assertEquals("${row.name} sits in a group of the wrong page", row.page, row.group.page)
        }
        SettingsAction.values().forEach { action ->
            assertEquals("${action.name} sits in a group of the wrong page", action.page, action.group.page)
        }
    }

    @Test fun groupsAppearInTheSpecifiedOrderOnEachPage() {
        assertEquals(
            listOf(SettingsGroup.THEME, SettingsGroup.COLOUR, SettingsGroup.TEXT, SettingsGroup.INTERFACE, SettingsGroup.MOTION),
            SettingsGroup.of(SettingsPage.APPEARANCE),
        )
        assertEquals(
            listOf(SettingsGroup.A11Y_TEXT, SettingsGroup.A11Y_MOTION, SettingsGroup.A11Y_VISUAL, SettingsGroup.A11Y_INTERACTION),
            SettingsGroup.of(SettingsPage.ACCESSIBILITY),
        )
        assertEquals(
            listOf(SettingsGroup.PRIVACY_GENERAL, SettingsGroup.PRIVATE_KEYS),
            SettingsGroup.of(SettingsPage.PRIVACY),
        )
        assertEquals(
            listOf(SettingsGroup.DATA_GENERAL, SettingsGroup.SAVED_DATA, SettingsGroup.DANGER_ZONE),
            SettingsGroup.of(SettingsPage.DATA),
        )
        assertEquals(
            listOf(SettingsGroup.PROCESSING, SettingsGroup.FEEDBACK, SettingsGroup.DIAGNOSTICS, SettingsGroup.TOOL_CONFIGURATION),
            SettingsGroup.of(SettingsPage.ADVANCED),
        )
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
        assertEquals(
            setOf(
                SettingRow.THEME, SettingRow.ACCENT, SettingRow.DYNAMIC_COLOR, SettingRow.TEXT_SIZE,
                SettingRow.LAYOUT_DENSITY, SettingRow.SHOW_TOOL_ICONS, SettingRow.MONOSPACE_OUTPUT, SettingRow.UI_ANIMATION,
            ),
            on(SettingsPage.APPEARANCE),
        )
        assertEquals(
            setOf(
                SettingRow.LARGE_TEXT, SettingRow.REDUCE_ANIMATIONS, SettingRow.HIGH_CONTRAST,
                SettingRow.ICON_LABELS, SettingRow.LARGE_TOUCH_TARGETS, SettingRow.HAPTICS,
            ),
            on(SettingsPage.ACCESSIBILITY),
        )
        assertEquals(
            setOf(
                SettingRow.SENSITIVE_WARNINGS, SettingRow.CLEAR_ON_TOOL_SWITCH, SettingRow.CLEAR_ON_BACKGROUND,
                SettingRow.HIDE_PRIVATE_KEY_PREVIEW, SettingRow.CONFIRM_PRIVATE_KEY_COPY,
            ),
            on(SettingsPage.PRIVACY),
        )
        assertEquals(
            setOf(
                SettingRow.AUTO_PROCESS, SettingRow.SHOW_PROCESSING_TIME, SettingRow.COPY_CONFIRMATION,
                SettingRow.SHOW_TOOL_ID, SettingRow.SHOW_DETECTION_DETAILS, SettingRow.SHOW_VALIDATION_DETAILS,
            ),
            on(SettingsPage.ADVANCED),
        )
        assertTrue(on(SettingsPage.DATA).isEmpty())
        assertTrue(on(SettingsPage.ABOUT).isEmpty())
        assertTrue(on(SettingsPage.ROOT).isEmpty())
    }

    @Test fun rowsWithinGroupsAppearInTheSpecifiedOrder() {
        assertEquals(listOf(SettingRow.THEME), SettingRow.of(SettingsGroup.THEME))
        assertEquals(listOf(SettingRow.ACCENT, SettingRow.DYNAMIC_COLOR), SettingRow.of(SettingsGroup.COLOUR))
        assertEquals(
            listOf(SettingRow.LAYOUT_DENSITY, SettingRow.SHOW_TOOL_ICONS, SettingRow.MONOSPACE_OUTPUT),
            SettingRow.of(SettingsGroup.INTERFACE),
        )
        assertEquals(
            listOf(SettingRow.LARGE_TOUCH_TARGETS, SettingRow.ICON_LABELS, SettingRow.HAPTICS),
            SettingRow.of(SettingsGroup.A11Y_INTERACTION),
        )
        assertEquals(
            listOf(SettingRow.SENSITIVE_WARNINGS, SettingRow.CLEAR_ON_TOOL_SWITCH, SettingRow.CLEAR_ON_BACKGROUND),
            SettingRow.of(SettingsGroup.PRIVACY_GENERAL),
        )
        assertEquals(
            listOf(SettingRow.SHOW_TOOL_ID, SettingRow.SHOW_DETECTION_DETAILS, SettingRow.SHOW_VALIDATION_DETAILS),
            SettingRow.of(SettingsGroup.DIAGNOSTICS),
        )
    }

    @Test fun privateKeyControlsAreSeparatedFromGeneralPrivacy() {
        val general = SettingRow.of(SettingsGroup.PRIVACY_GENERAL).toSet()
        val keys = SettingRow.of(SettingsGroup.PRIVATE_KEYS).toSet()
        assertEquals(setOf(SettingRow.HIDE_PRIVATE_KEY_PREVIEW, SettingRow.CONFIRM_PRIVATE_KEY_COPY), keys)
        assertTrue(general.none { it.prefKey.contains("private") })
    }

    @Test fun diagnosticsStaysAtTheQuietEndOfAdvanced() {
        val groups = SettingsGroup.of(SettingsPage.ADVANCED)
        // Diagnostics is rendered, but after the everyday groups and before the reset action.
        assertTrue(groups.indexOf(SettingsGroup.DIAGNOSTICS) > groups.indexOf(SettingsGroup.PROCESSING))
        assertTrue(groups.indexOf(SettingsGroup.DIAGNOSTICS) > groups.indexOf(SettingsGroup.FEEDBACK))
        assertEquals(SettingsGroup.TOOL_CONFIGURATION, groups.last())
        // ...and never leaks onto the root or the more prominent pages.
        assertTrue(SettingsGroup.DIAGNOSTICS.page == SettingsPage.ADVANCED)
    }

    @Test fun keysAndDefaultsAreUnchangedByTheRedesign() {
        // The rows point at the same keys previous versions stored, so saved settings carry over.
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
        // The danger zone holds exactly the one heaviest action.
        assertEquals(listOf(SettingsAction.CLEAR_EVERYTHING), SettingsAction.of(SettingsGroup.DANGER_ZONE))
        assertEquals(
            listOf(SettingsAction.RESTORE_PREFERENCES, SettingsAction.RESET_TOOL_SETTINGS, SettingsAction.RESET_FAVORITES),
            SettingsAction.of(SettingsGroup.DATA_GENERAL),
        )
        // The same operation is also offered where troubleshooting happens, still non-destructive.
        assertEquals(SettingsPage.ADVANCED, SettingsAction.RESET_TOOL_SETTINGS_FROM_ADVANCED.page)
        assertEquals(SettingsGroup.TOOL_CONFIGURATION, SettingsAction.RESET_TOOL_SETTINGS_FROM_ADVANCED.group)
        assertFalse(SettingsAction.RESET_TOOL_SETTINGS_FROM_ADVANCED.destructive)
    }

    // ----------------------------------------------------------------- navigation

    @Test fun backFromAnyPageReturnsToTheRootAndThenLeaves() {
        var nav = SettingsNavigation()
        assertTrue(nav.atRoot)
        nav = nav.open(SettingsPage.APPEARANCE)
        assertEquals(SettingsPage.APPEARANCE, nav.page)
        nav = nav.back()!!
        assertTrue(nav.atRoot)
        assertNull("back at the root means leaving Settings", nav.back())
    }

    @Test fun everyDestinationIsAtMostTwoTapsFromTheRoot() {
        assertEquals(SettingsPage.ROOT, SettingsNavigation().page)
        SettingsPage.values().forEach { page ->
            var nav = SettingsNavigation().open(page)
            var steps = 0
            while (!nav.atRoot) { nav = nav.back()!!; steps++ }
            assertEquals(page.depth, steps)
            assertTrue("${page.name} is more than one tap deep", steps <= 1)
        }
    }
}
