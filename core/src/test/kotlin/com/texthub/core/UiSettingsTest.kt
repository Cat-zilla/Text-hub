package com.texthub.core

import com.texthub.core.prefs.AnimationMode
import com.texthub.core.prefs.CornerStyle
import com.texthub.core.prefs.KEPT_WHEN_CLEARING
import com.texthub.core.prefs.LayoutDensity
import com.texthub.core.prefs.PrefsData
import com.texthub.core.prefs.PrefsKeys
import com.texthub.core.prefs.UiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 1.7.0 settings: their defaults, that they survive a round trip through the preference store,
 * and that every reset treats them exactly as the settings screen says.
 */
class UiSettingsTest {

    private fun data(vararg pairs: Pair<String, String>) = PrefsData(mapOf(*pairs))

    // ------------------------------------------------------------------------- defaults

    @Test fun defaultsMatchTheSettingsSpecification() {
        val d = UiSettings.DEFAULT
        assertFalse(d.dynamicColor)
        assertEquals(LayoutDensity.COMFORTABLE, d.density)
        assertTrue(d.showToolIcons)
        assertTrue("the output box has always been monospace", d.monospaceOutput)
        assertFalse(d.largeText)
        assertEquals(AnimationMode.FULL, d.animation)
        assertEquals("the app as it always was", CornerStyle.ROUNDED, d.cornerStyle)
        assertFalse(d.highContrast)
        assertFalse(d.iconLabels)
        assertFalse(d.largeTouchTargets)
        assertTrue("existing behaviour: secrets are never carried to another tool", d.clearSecretsOnToolSwitch)
        assertFalse(d.clearSecretsOnBackground)
        assertTrue(d.confirmPrivateKeyCopy)
        assertTrue(d.hidePrivateKeyPreview)
        assertTrue(d.sensitiveWarnings)
        assertFalse(d.showProcessingTime)
        assertFalse(d.showToolId)
        assertFalse(d.showDetectionDetails)
        assertFalse(d.showValidationDetails)
    }

    @Test fun anEmptyStoreReadsAsTheDefaults() {
        assertEquals(UiSettings.DEFAULT, PrefsData().uiSettings())
    }

    @Test fun unknownStoredValuesFallBackToTheDefaults() {
        val stored = data(
            PrefsKeys.LAYOUT_DENSITY to "gigantic",
            PrefsKeys.UI_ANIMATION to "bouncy",
            PrefsKeys.DYNAMIC_COLOR to "maybe",
            PrefsKeys.CORNER_STYLE to "octagonal",
        )
        val s = stored.uiSettings()
        assertEquals(LayoutDensity.COMFORTABLE, s.density)
        assertEquals(AnimationMode.FULL, s.animation)
        assertFalse(s.dynamicColor)
        assertEquals(CornerStyle.ROUNDED, s.cornerStyle)
    }

    // ---------------------------------------------------------------------- persistence

    private val everythingFlipped = UiSettings(
        dynamicColor = true, density = LayoutDensity.COMPACT, showToolIcons = false, monospaceOutput = false,
        largeText = true, animation = AnimationMode.OFF, cornerStyle = CornerStyle.SQUARE,
        highContrast = true, iconLabels = true,
        largeTouchTargets = true, clearSecretsOnToolSwitch = false, clearSecretsOnBackground = true,
        confirmPrivateKeyCopy = false, hidePrivateKeyPreview = false, sensitiveWarnings = false,
        showProcessingTime = true, showToolId = true, showDetectionDetails = true, showValidationDetails = true,
    )

    @Test fun everySettingSurvivesARoundTrip() {
        val stored = PrefsData().withUiSettings(everythingFlipped)
        assertEquals(everythingFlipped, stored.uiSettings())
        val reduced = everythingFlipped.copy(animation = AnimationMode.REDUCED)
        assertEquals(reduced, PrefsData().withUiSettings(reduced).uiSettings())
    }

    @Test fun writingSettingsLeavesOtherPreferencesAlone() {
        val stored = data(
            PrefsKeys.FAVORITES_ORDER to "aes|base64",
            PrefsKeys.THEME to "AMOLED",
            "${PrefsKeys.PARAMS_PREFIX}aes" to "keySize=128",
        ).withUiSettings(everythingFlipped)
        assertEquals("aes|base64", stored.string(PrefsKeys.FAVORITES_ORDER))
        assertEquals("AMOLED", stored.string(PrefsKeys.THEME))
        assertEquals("keySize=128", stored.string("${PrefsKeys.PARAMS_PREFIX}aes"))
    }

    @Test fun theKeyListCoversEveryStoredKey() {
        val written = PrefsData().withUiSettings(everythingFlipped).entries.keys
        assertEquals(written, UiSettings.KEYS.toSet())
        assertEquals(19, UiSettings.KEYS.size)
        assertTrue(PrefsKeys.CORNER_STYLE in UiSettings.KEYS)
    }

    @Test fun storedValuesContainNoTextOrSecretShapedData() {
        val values = PrefsData().withUiSettings(everythingFlipped).entries.values
        val allowed = setOf("true", "false") + LayoutDensity.values().map { it.id } +
            AnimationMode.values().map { it.id } + CornerStyle.values().map { it.id }
        values.forEach { assertTrue("unexpected stored value '$it'", it in allowed) }
    }

    // --------------------------------------------------------------------- reset semantics

    @Test fun restoringDefaultsResetsEverySettingAndKeepsFavourites() {
        val stored = data(PrefsKeys.FAVORITES_ORDER to "aes|base64|vigenere").withUiSettings(everythingFlipped)
        val after = stored.restoredToDefaults()
        assertEquals(UiSettings.DEFAULT, after.uiSettings())
        assertEquals("the corner style returns to Rounded", CornerStyle.ROUNDED, after.uiSettings().cornerStyle)
        assertEquals(listOf("aes", "base64", "vigenere"), after.favorites())
        assertEquals(setOf(PrefsKeys.FAVORITES_ORDER), after.entries.keys)
    }

    @Test fun clearingTemporaryDataKeepsEverySetting() {
        val stored = data(
            PrefsKeys.RECENTS to "aes|base64",
            "${PrefsKeys.PARAMS_PREFIX}aes" to "keySize=128",
        ).withUiSettings(everythingFlipped)
        val after = stored.clearTemporary()
        assertEquals(everythingFlipped, after.uiSettings())
        assertEquals("reset remembered tool settings leaves the corner style alone", CornerStyle.SQUARE, after.uiSettings().cornerStyle)
        assertNull(after.string(PrefsKeys.RECENTS))
        assertNull(after.string("${PrefsKeys.PARAMS_PREFIX}aes"))
        UiSettings.KEYS.forEach { assertTrue("$it must be kept when clearing", it in KEPT_WHEN_CLEARING) }
    }

    @Test fun resettingFavouritesRemovesOnlyFavourites() {
        val stored = data(
            PrefsKeys.FAVORITES_ORDER to "aes|base64",
            PrefsKeys.FAVORITES_LEGACY to "aes",
            PrefsKeys.RECENTS to "aes",
            PrefsKeys.THEME to "AMOLED",
            "${PrefsKeys.PARAMS_PREFIX}aes" to "keySize=128",
        ).withUiSettings(everythingFlipped)
        val after = stored.withoutFavorites()
        assertTrue(after.favorites().isEmpty())
        assertEquals("reset favourites leaves the corner style alone", CornerStyle.SQUARE, after.uiSettings().cornerStyle)
        assertEquals("aes", after.string(PrefsKeys.RECENTS))
        assertEquals("AMOLED", after.string(PrefsKeys.THEME))
        assertEquals("keySize=128", after.string("${PrefsKeys.PARAMS_PREFIX}aes"))
        assertEquals(everythingFlipped, after.uiSettings())
    }

    @Test fun resettingFavouritesWhenThereAreNoneIsANoOp() {
        val stored = data(PrefsKeys.THEME to "DARK")
        assertEquals(stored, stored.withoutFavorites())
    }

    // ------------------------------------------------------- one source of truth per option

    @Test fun reduceAnimationsIsAViewOfTheAnimationMode() {
        assertFalse(UiSettings(animation = AnimationMode.FULL).reduceAnimations)
        assertTrue(UiSettings(animation = AnimationMode.REDUCED).reduceAnimations)
        assertTrue(UiSettings(animation = AnimationMode.OFF).reduceAnimations)

        assertEquals(AnimationMode.REDUCED, UiSettings().withReduceAnimations(true).animation)
        // Turning the accessibility switch on does not downgrade an explicit "Off".
        assertEquals(AnimationMode.OFF, UiSettings(animation = AnimationMode.OFF).withReduceAnimations(true).animation)
        assertEquals(AnimationMode.FULL, UiSettings(animation = AnimationMode.OFF).withReduceAnimations(false).animation)
        assertEquals(AnimationMode.FULL, UiSettings(animation = AnimationMode.REDUCED).withReduceAnimations(false).animation)
    }

    @Test fun animationModesScaleDurationsAsDocumented() {
        assertEquals(160, AnimationMode.FULL.duration(160))
        assertEquals(80, AnimationMode.REDUCED.duration(160))
        assertEquals(0, AnimationMode.OFF.duration(160))
    }

    @Test fun idsRoundTripForEveryEnum() {
        LayoutDensity.values().forEach { assertEquals(it, LayoutDensity.fromId(it.id)) }
        AnimationMode.values().forEach { assertEquals(it, AnimationMode.fromId(it.id)) }
        CornerStyle.values().forEach { assertEquals(it, CornerStyle.fromId(it.id)) }
        assertEquals(LayoutDensity.COMFORTABLE, LayoutDensity.fromId(null))
        assertEquals(AnimationMode.FULL, AnimationMode.fromId(null))
        assertEquals(CornerStyle.ROUNDED, CornerStyle.fromId(null))
    }
}
