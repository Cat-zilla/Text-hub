package com.texthub.app.ui.settings

import com.texthub.core.prefs.PrefsKeys
import com.texthub.core.prefs.UiSettings

/**
 * The information architecture of Settings, as plain data so it can be unit tested without Compose:
 * which pages exist, how they nest, which preference lives on which page and what each destructive
 * action is. The composables in `SettingsScreen` render exactly this structure.
 *
 * Two levels at most: a root list of six destinations, and two second-level pages (Interface under
 * Appearance, Diagnostics under Advanced) for the low-frequency controls that would otherwise make
 * their parents dense. Nothing is deeper than that.
 */
enum class SettingsPage(val parent: SettingsPage?) {
    ROOT(null),
    APPEARANCE(ROOT),
    INTERFACE(APPEARANCE),
    ACCESSIBILITY(ROOT),
    PRIVACY(ROOT),
    DATA(ROOT),
    ADVANCED(ROOT),
    DIAGNOSTICS(ADVANCED),
    ABOUT(ROOT);

    /** Root first, this page last. */
    val path: List<SettingsPage> get() = generateSequence(this) { it.parent }.toList().asReversed()

    val depth: Int get() = path.size - 1

    companion object {
        /** The destinations the root screen lists, in order. */
        val TOP_LEVEL: List<SettingsPage> = listOf(APPEARANCE, ACCESSIBILITY, PRIVACY, DATA, ADVANCED, ABOUT)
    }
}

/**
 * Every user-changeable preference and the page it is shown on. [prefKey] is the stored key, so a
 * test can prove that each stored setting is reachable and that two rows sharing one key (Large
 * text / Text size, Reduce animations / UI animation) are deliberate, documented pairs.
 */
enum class SettingRow(val page: SettingsPage, val prefKey: String) {
    // Appearance
    THEME(SettingsPage.APPEARANCE, PrefsKeys.THEME),
    ACCENT(SettingsPage.APPEARANCE, PrefsKeys.ACCENT),
    DYNAMIC_COLOR(SettingsPage.APPEARANCE, PrefsKeys.DYNAMIC_COLOR),
    TEXT_SIZE(SettingsPage.APPEARANCE, PrefsKeys.LARGE_TEXT),
    // Appearance > Interface
    LAYOUT_DENSITY(SettingsPage.INTERFACE, PrefsKeys.LAYOUT_DENSITY),
    SHOW_TOOL_ICONS(SettingsPage.INTERFACE, PrefsKeys.SHOW_TOOL_ICONS),
    MONOSPACE_OUTPUT(SettingsPage.INTERFACE, PrefsKeys.MONOSPACE_OUTPUT),
    UI_ANIMATION(SettingsPage.INTERFACE, PrefsKeys.UI_ANIMATION),
    // Accessibility
    LARGE_TEXT(SettingsPage.ACCESSIBILITY, PrefsKeys.LARGE_TEXT),
    REDUCE_ANIMATIONS(SettingsPage.ACCESSIBILITY, PrefsKeys.UI_ANIMATION),
    HIGH_CONTRAST(SettingsPage.ACCESSIBILITY, PrefsKeys.HIGH_CONTRAST),
    ICON_LABELS(SettingsPage.ACCESSIBILITY, PrefsKeys.ICON_LABELS),
    LARGE_TOUCH_TARGETS(SettingsPage.ACCESSIBILITY, PrefsKeys.LARGE_TOUCH_TARGETS),
    HAPTICS(SettingsPage.ACCESSIBILITY, PrefsKeys.HAPTIC_FEEDBACK),
    // Privacy & security
    CLEAR_ON_TOOL_SWITCH(SettingsPage.PRIVACY, PrefsKeys.CLEAR_SECRETS_ON_TOOL_SWITCH),
    CLEAR_ON_BACKGROUND(SettingsPage.PRIVACY, PrefsKeys.CLEAR_SECRETS_ON_BACKGROUND),
    SENSITIVE_WARNINGS(SettingsPage.PRIVACY, PrefsKeys.SENSITIVE_WARNINGS),
    CONFIRM_PRIVATE_KEY_COPY(SettingsPage.PRIVACY, PrefsKeys.CONFIRM_PRIVATE_KEY_COPY),
    HIDE_PRIVATE_KEY_PREVIEW(SettingsPage.PRIVACY, PrefsKeys.HIDE_PRIVATE_KEY_PREVIEW),
    // Advanced
    AUTO_PROCESS(SettingsPage.ADVANCED, PrefsKeys.AUTO_PROCESS),
    SHOW_PROCESSING_TIME(SettingsPage.ADVANCED, PrefsKeys.SHOW_PROCESSING_TIME),
    COPY_CONFIRMATION(SettingsPage.ADVANCED, PrefsKeys.COPY_CONFIRMATION),
    // Advanced > Diagnostics
    SHOW_TOOL_ID(SettingsPage.DIAGNOSTICS, PrefsKeys.SHOW_TOOL_ID),
    SHOW_DETECTION_DETAILS(SettingsPage.DIAGNOSTICS, PrefsKeys.SHOW_DETECTION_DETAILS),
    SHOW_VALIDATION_DETAILS(SettingsPage.DIAGNOSTICS, PrefsKeys.SHOW_VALIDATION_DETAILS);

    companion object {
        /** The pairs that intentionally share one stored key: two views, one source of truth. */
        val SHARED_KEY_PAIRS: Set<Set<SettingRow>> = setOf(
            setOf(TEXT_SIZE, LARGE_TEXT),
            setOf(UI_ANIMATION, REDUCE_ANIMATIONS),
        )

        /** Every stored key the settings UI must give access to. */
        val REQUIRED_KEYS: Set<String> = UiSettings.KEYS.toSet() + setOf(
            PrefsKeys.THEME, PrefsKeys.ACCENT, PrefsKeys.AUTO_PROCESS,
            PrefsKeys.COPY_CONFIRMATION, PrefsKeys.HAPTIC_FEEDBACK,
        )
    }
}

/** The reset actions, where they live and whether they are drawn as destructive. */
enum class SettingsAction(val page: SettingsPage, val destructive: Boolean, val confirmations: Int) {
    RESTORE_PREFERENCES(SettingsPage.DATA, destructive = false, confirmations = 1),
    RESET_TOOL_SETTINGS(SettingsPage.DATA, destructive = false, confirmations = 1),
    RESET_FAVORITES(SettingsPage.DATA, destructive = false, confirmations = 1),
    CLEAR_SAVED_RSA_KEYS(SettingsPage.DATA, destructive = true, confirmations = 1),
    CLEAR_EVERYTHING(SettingsPage.DATA, destructive = true, confirmations = 2),
    /** The same operation as [RESET_TOOL_SETTINGS], also offered where troubleshooting happens. */
    RESET_TOOL_SETTINGS_FROM_DIAGNOSTICS(SettingsPage.DIAGNOSTICS, destructive = false, confirmations = 1);
}

/**
 * The back stack of Settings: a page and the way back through its parents. Pure, so the back
 * behaviour is testable; the screen holds one of these in remembered state.
 */
data class SettingsNavigation(val page: SettingsPage = SettingsPage.ROOT) {
    fun open(target: SettingsPage): SettingsNavigation = copy(page = target)

    /** One step up, or null when already at the root (the caller then leaves Settings). */
    fun back(): SettingsNavigation? = page.parent?.let { copy(page = it) }

    val atRoot: Boolean get() = page == SettingsPage.ROOT
}
