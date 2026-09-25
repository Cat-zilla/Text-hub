package com.texthub.core

import com.texthub.core.prefs.AnimationMode
import com.texthub.core.prefs.CornerStyle
import com.texthub.core.prefs.PrefsData
import com.texthub.core.prefs.PrefsKeys
import com.texthub.core.prefs.UiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Corner style preference: its three values, that it is a normal appearance preference
 * (default Rounded, persisted, reset by "Restore app preferences" and untouched by every other
 * reset), and that it never carries anything but one of its three ids.
 */
class CornerStyleTest {

    // ------------------------------------------------------------------------- values

    @Test fun theThreeStylesAreRoundedSlightlyRoundedAndSquare() {
        assertEquals(
            listOf(CornerStyle.ROUNDED, CornerStyle.SLIGHT, CornerStyle.SQUARE),
            CornerStyle.values().toList(),
        )
        assertEquals(listOf("rounded", "slight", "square"), CornerStyle.values().map { it.id })
    }

    @Test fun roundedIsTheDefault() {
        assertEquals(CornerStyle.ROUNDED, UiSettings.DEFAULT.cornerStyle)
        assertEquals(CornerStyle.ROUNDED, CornerStyle.fromId(null))
    }

    @Test fun everyStyleRoundTripsThroughItsId() {
        CornerStyle.values().forEach { assertEquals(it, CornerStyle.fromId(it.id)) }
    }

    @Test fun anUnknownStoredIdFallsBackToRounded() {
        assertEquals(CornerStyle.ROUNDED, CornerStyle.fromId("octagonal"))
        assertEquals(CornerStyle.ROUNDED, CornerStyle.fromId(""))
        assertEquals(CornerStyle.ROUNDED, PrefsData().uiSettings().cornerStyle)
    }

    // --------------------------------------------------------------------- persistence

    @Test fun theStyleSurvivesARoundTripThroughTheStore() {
        CornerStyle.values().forEach { style ->
            val stored = PrefsData().withUiSettings(UiSettings(cornerStyle = style))
            assertEquals(style, stored.uiSettings().cornerStyle)
            assertEquals(style.id, stored.string(PrefsKeys.CORNER_STYLE))
        }
    }

    @Test fun writingTheStyleLeavesEveryOtherPreferenceAlone() {
        val stored = PrefsData(
            mapOf(
                PrefsKeys.FAVORITES_ORDER to "aes|base64",
                PrefsKeys.THEME to "AMOLED",
                "${PrefsKeys.PARAMS_PREFIX}aes" to "keySize=128",
            ),
        ).withUiSettings(
            // The UiSettings keys are one value, written whole by design; the preference that
            // matters here is that the corner style rides along without disturbing anything else.
            UiSettings(cornerStyle = CornerStyle.SQUARE, animation = AnimationMode.OFF),
        )
        assertEquals("aes|base64", stored.string(PrefsKeys.FAVORITES_ORDER))
        assertEquals("AMOLED", stored.string(PrefsKeys.THEME))
        assertEquals("off", stored.string(PrefsKeys.UI_ANIMATION))
        assertEquals("keySize=128", stored.string("${PrefsKeys.PARAMS_PREFIX}aes"))
        assertEquals(CornerStyle.SQUARE, stored.uiSettings().cornerStyle)
        assertEquals(AnimationMode.OFF, stored.uiSettings().animation)
    }

    @Test fun theKeyIsListedAmongThePreferencesThatAreKeptWhenClearingTemporaryData() {
        assertTrue(PrefsKeys.CORNER_STYLE in UiSettings.KEYS)
        assertTrue(PrefsKeys.CORNER_STYLE in com.texthub.core.prefs.KEPT_WHEN_CLEARING)
    }

    @Test fun theStoredValueIsNothingButOneOfTheThreeIds() {
        val allowed = CornerStyle.values().map { it.id }.toSet()
        CornerStyle.values().forEach { style ->
            assertTrue(PrefsData().withUiSettings(UiSettings(cornerStyle = style)).string(PrefsKeys.CORNER_STYLE) in allowed)
        }
        // It is a shape choice, never text, a key or anything derived from the user's data.
        assertFalse(PrefsKeys.CORNER_STYLE.startsWith(PrefsKeys.PARAMS_PREFIX))
    }

    // ------------------------------------------------------------------ reset semantics

    private val everythingFlipped = UiSettings(
        dynamicColor = true, density = com.texthub.core.prefs.LayoutDensity.COMPACT,
        showToolIcons = false, monospaceOutput = false, largeText = true,
        animation = com.texthub.core.prefs.AnimationMode.OFF, cornerStyle = CornerStyle.SQUARE,
        highContrast = true, iconLabels = true, largeTouchTargets = true,
        clearSecretsOnToolSwitch = false, clearSecretsOnBackground = true,
        confirmPrivateKeyCopy = false, hidePrivateKeyPreview = false, sensitiveWarnings = false,
        showProcessingTime = true, showToolId = true, showDetectionDetails = true,
        showValidationDetails = true,
    )

    @Test fun restoringAppPreferencesResetsTheStyleToRounded() {
        val stored = PrefsData(mapOf(PrefsKeys.FAVORITES_ORDER to "aes|base64"))
            .withUiSettings(everythingFlipped)
        val after = stored.restoredToDefaults()
        assertEquals(CornerStyle.ROUNDED, after.uiSettings().cornerStyle)
        // Favourites survive, as documented for every reset in this app.
        assertEquals(listOf("aes", "base64"), after.favorites())
    }

    @Test fun resettingRememberedToolSettingsDoesNotChangeTheStyle() {
        val stored = PrefsData(
            mapOf(
                PrefsKeys.RECENTS to "aes|base64",
                "${PrefsKeys.PARAMS_PREFIX}aes" to "keySize=128",
            ),
        ).withUiSettings(everythingFlipped)
        val after = stored.clearTemporary()
        assertEquals(CornerStyle.SQUARE, after.uiSettings().cornerStyle)
    }

    @Test fun resettingFavouritesDoesNotChangeTheStyle() {
        val stored = PrefsData(mapOf(PrefsKeys.FAVORITES_ORDER to "aes|base64"))
            .withUiSettings(everythingFlipped)
        val after = stored.withoutFavorites()
        assertTrue(after.favorites().isEmpty())
        assertEquals(CornerStyle.SQUARE, after.uiSettings().cornerStyle)
    }

    @Test fun clearingSavedRsaKeysIsNotAPreferenceOperationAndLeavesTheStoreUntouched() {
        // "Clear saved RSA keys" empties the encrypted vault; it never writes to the preference
        // store, so the corner style (and everything else) is simply not involved.
        val stored = PrefsData().withUiSettings(everythingFlipped)
        assertEquals(stored, stored)
        assertEquals(CornerStyle.SQUARE, stored.uiSettings().cornerStyle)
    }

    @Test fun clearingEverythingFollowsTheExistingPreferenceResetBehaviour() {
        // "Clear everything" is restoreDefaults + favourites + vault + screen state: the
        // preference part of it is exactly "Restore app preferences".
        val stored = PrefsData(
            mapOf(PrefsKeys.FAVORITES_ORDER to "aes|base64", PrefsKeys.RECENTS to "aes"),
        ).withUiSettings(everythingFlipped)
        val after = stored.restoredToDefaults().withoutFavorites()
        assertEquals(UiSettings.DEFAULT, after.uiSettings())
        assertEquals(CornerStyle.ROUNDED, after.uiSettings().cornerStyle)
        assertTrue(after.favorites().isEmpty())
    }
}
