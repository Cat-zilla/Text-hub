package com.texthub.app.ui.settings

import com.texthub.core.prefs.PrefsKeys
import com.texthub.core.prefs.UiSettings

/**
 * The information architecture of Settings, as plain data so it can be unit tested without Compose:
 * which pages exist, which group of each page a preference belongs to and what each destructive
 * action is. The composables in `SettingsScreen` render exactly this structure.
 *
 * One level only: a root list of six destinations, and six flat pages. There are no second-level
 * pages - low-frequency controls live in visually quieter groups (Diagnostics at the bottom of
 * Advanced) instead of behind another screen, so every setting is at most two taps away and each
 * page can be scanned in one scroll.
 */
enum class SettingsPage(val parent: SettingsPage?) {
    ROOT(null),
    APPEARANCE(ROOT),
    ACCESSIBILITY(ROOT),
    PRIVACY(ROOT),
    DATA(ROOT),
    ADVANCED(ROOT),
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
 * A labelled group of rows on one Settings page, top to bottom in declaration order. Groups are
 * the unit of visual hierarchy: a page is a short stack of groups, not a flat list of rows.
 */
enum class SettingsGroup(val page: SettingsPage) {
    // Appearance
    THEME(SettingsPage.APPEARANCE),
    COLOUR(SettingsPage.APPEARANCE),
    TEXT(SettingsPage.APPEARANCE),
    INTERFACE(SettingsPage.APPEARANCE),
    MOTION(SettingsPage.APPEARANCE),
    // Accessibility
    A11Y_TEXT(SettingsPage.ACCESSIBILITY),
    A11Y_MOTION(SettingsPage.ACCESSIBILITY),
    A11Y_VISUAL(SettingsPage.ACCESSIBILITY),
    A11Y_INTERACTION(SettingsPage.ACCESSIBILITY),
    // Privacy & security
    PRIVACY_GENERAL(SettingsPage.PRIVACY),
    PRIVATE_KEYS(SettingsPage.PRIVACY),
    // Data & reset
    DATA_GENERAL(SettingsPage.DATA),
    SAVED_DATA(SettingsPage.DATA),
    DANGER_ZONE(SettingsPage.DATA),
    // Advanced
    PROCESSING(SettingsPage.ADVANCED),
    FEEDBACK(SettingsPage.ADVANCED),
    DIAGNOSTICS(SettingsPage.ADVANCED),
    TOOL_CONFIGURATION(SettingsPage.ADVANCED);

    companion object {
        /** The groups of [page], top to bottom. */
        fun of(page: SettingsPage): List<SettingsGroup> = values().filter { it.page == page }
    }
}

/**
 * Every user-changeable preference, the page and group it is shown in. [prefKey] is the stored
 * key, so a test can prove that each stored setting is reachable and that two rows sharing one key
 * (Large text / Text size, Reduce animations / UI animation) are deliberate, documented pairs.
 *
 * Declaration order inside a group is the display order.
 */
enum class SettingRow(val page: SettingsPage, val group: SettingsGroup, val prefKey: String) {
    // Appearance > Theme
    THEME(SettingsPage.APPEARANCE, SettingsGroup.THEME, PrefsKeys.THEME),
    // Appearance > Colour
    ACCENT(SettingsPage.APPEARANCE, SettingsGroup.COLOUR, PrefsKeys.ACCENT),
    DYNAMIC_COLOR(SettingsPage.APPEARANCE, SettingsGroup.COLOUR, PrefsKeys.DYNAMIC_COLOR),
    // Appearance > Text
    TEXT_SIZE(SettingsPage.APPEARANCE, SettingsGroup.TEXT, PrefsKeys.LARGE_TEXT),
    // Appearance > Interface
    LAYOUT_DENSITY(SettingsPage.APPEARANCE, SettingsGroup.INTERFACE, PrefsKeys.LAYOUT_DENSITY),
    SHOW_TOOL_ICONS(SettingsPage.APPEARANCE, SettingsGroup.INTERFACE, PrefsKeys.SHOW_TOOL_ICONS),
    MONOSPACE_OUTPUT(SettingsPage.APPEARANCE, SettingsGroup.INTERFACE, PrefsKeys.MONOSPACE_OUTPUT),
    // Appearance > Motion
    UI_ANIMATION(SettingsPage.APPEARANCE, SettingsGroup.MOTION, PrefsKeys.UI_ANIMATION),
    // Accessibility > Text
    LARGE_TEXT(SettingsPage.ACCESSIBILITY, SettingsGroup.A11Y_TEXT, PrefsKeys.LARGE_TEXT),
    // Accessibility > Motion
    REDUCE_ANIMATIONS(SettingsPage.ACCESSIBILITY, SettingsGroup.A11Y_MOTION, PrefsKeys.UI_ANIMATION),
    // Accessibility > Visual
    HIGH_CONTRAST(SettingsPage.ACCESSIBILITY, SettingsGroup.A11Y_VISUAL, PrefsKeys.HIGH_CONTRAST),
    // Accessibility > Interaction
    LARGE_TOUCH_TARGETS(SettingsPage.ACCESSIBILITY, SettingsGroup.A11Y_INTERACTION, PrefsKeys.LARGE_TOUCH_TARGETS),
    ICON_LABELS(SettingsPage.ACCESSIBILITY, SettingsGroup.A11Y_INTERACTION, PrefsKeys.ICON_LABELS),
    HAPTICS(SettingsPage.ACCESSIBILITY, SettingsGroup.A11Y_INTERACTION, PrefsKeys.HAPTIC_FEEDBACK),
    // Privacy & security > Privacy
    SENSITIVE_WARNINGS(SettingsPage.PRIVACY, SettingsGroup.PRIVACY_GENERAL, PrefsKeys.SENSITIVE_WARNINGS),
    CLEAR_ON_TOOL_SWITCH(SettingsPage.PRIVACY, SettingsGroup.PRIVACY_GENERAL, PrefsKeys.CLEAR_SECRETS_ON_TOOL_SWITCH),
    CLEAR_ON_BACKGROUND(SettingsPage.PRIVACY, SettingsGroup.PRIVACY_GENERAL, PrefsKeys.CLEAR_SECRETS_ON_BACKGROUND),
    // Privacy & security > Private keys
    HIDE_PRIVATE_KEY_PREVIEW(SettingsPage.PRIVACY, SettingsGroup.PRIVATE_KEYS, PrefsKeys.HIDE_PRIVATE_KEY_PREVIEW),
    CONFIRM_PRIVATE_KEY_COPY(SettingsPage.PRIVACY, SettingsGroup.PRIVATE_KEYS, PrefsKeys.CONFIRM_PRIVATE_KEY_COPY),
    // Advanced > Processing
    AUTO_PROCESS(SettingsPage.ADVANCED, SettingsGroup.PROCESSING, PrefsKeys.AUTO_PROCESS),
    SHOW_PROCESSING_TIME(SettingsPage.ADVANCED, SettingsGroup.PROCESSING, PrefsKeys.SHOW_PROCESSING_TIME),
    // Advanced > Feedback
    COPY_CONFIRMATION(SettingsPage.ADVANCED, SettingsGroup.FEEDBACK, PrefsKeys.COPY_CONFIRMATION),
    // Advanced > Diagnostics
    SHOW_TOOL_ID(SettingsPage.ADVANCED, SettingsGroup.DIAGNOSTICS, PrefsKeys.SHOW_TOOL_ID),
    SHOW_DETECTION_DETAILS(SettingsPage.ADVANCED, SettingsGroup.DIAGNOSTICS, PrefsKeys.SHOW_DETECTION_DETAILS),
    SHOW_VALIDATION_DETAILS(SettingsPage.ADVANCED, SettingsGroup.DIAGNOSTICS, PrefsKeys.SHOW_VALIDATION_DETAILS);

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

        /** The rows of [group], top to bottom. */
        fun of(group: SettingsGroup): List<SettingRow> = values().filter { it.group == group }
    }
}

/** The reset actions, where they live and whether they are drawn as destructive. */
enum class SettingsAction(val page: SettingsPage, val group: SettingsGroup, val destructive: Boolean, val confirmations: Int) {
    RESTORE_PREFERENCES(SettingsPage.DATA, SettingsGroup.DATA_GENERAL, destructive = false, confirmations = 1),
    RESET_TOOL_SETTINGS(SettingsPage.DATA, SettingsGroup.DATA_GENERAL, destructive = false, confirmations = 1),
    RESET_FAVORITES(SettingsPage.DATA, SettingsGroup.DATA_GENERAL, destructive = false, confirmations = 1),
    CLEAR_SAVED_RSA_KEYS(SettingsPage.DATA, SettingsGroup.SAVED_DATA, destructive = true, confirmations = 1),
    CLEAR_EVERYTHING(SettingsPage.DATA, SettingsGroup.DANGER_ZONE, destructive = true, confirmations = 2),
    /** The same operation as [RESET_TOOL_SETTINGS], also offered where troubleshooting happens. */
    RESET_TOOL_SETTINGS_FROM_ADVANCED(SettingsPage.ADVANCED, SettingsGroup.TOOL_CONFIGURATION, destructive = false, confirmations = 1);

    companion object {
        /** The actions of [group], top to bottom. */
        fun of(group: SettingsGroup): List<SettingsAction> = values().filter { it.group == group }
    }
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
