package com.texthub.core.prefs

/** Vertical rhythm of the screens. Compact trims spacing only; touch targets are never reduced. */
enum class LayoutDensity(val id: String) {
    COMFORTABLE("comfortable"),
    COMPACT("compact");

    companion object {
        fun fromId(id: String?): LayoutDensity = values().firstOrNull { it.id == id } ?: COMFORTABLE
    }
}

/**
 * Non-essential UI motion. FULL is the app as it always was; REDUCED shortens transitions; OFF snaps.
 * Processing indicators, drag feedback and state changes are never affected - only decoration.
 */
enum class AnimationMode(val id: String) {
    FULL("full"),
    REDUCED("reduced"),
    OFF("off");

    /** Scales a nominal duration in milliseconds; 0 means "no animation, apply the end state". */
    fun duration(nominalMs: Int): Int = when (this) {
        FULL -> nominalMs
        REDUCED -> nominalMs / 2
        OFF -> 0
    }

    companion object {
        fun fromId(id: String?): AnimationMode = values().firstOrNull { it.id == id } ?: FULL
    }
}

/**
 * The settings added in the 1.7.0 settings round, as one plain value with its defaults, read from
 * and written to [PrefsData] so the semantics can be unit tested without Android.
 *
 * Rules that hold for every field here:
 *  * it is an ordinary preference: "Restore app preferences" returns it to the default below,
 *    "Reset remembered tool settings" never touches it;
 *  * it never carries text, a password, a key or anything derived from them;
 *  * one preference is one source of truth: *Large text* is the same value whether it is changed
 *    from Appearance (text size) or Accessibility (large text), and *Reduce animations* is the same
 *    value as the UI animation mode (reduce = not FULL).
 */
data class UiSettings(
    // Appearance
    val dynamicColor: Boolean = false,
    val density: LayoutDensity = LayoutDensity.COMFORTABLE,
    val showToolIcons: Boolean = true,
    /** The output box has always been monospace; the default keeps that behaviour. */
    val monospaceOutput: Boolean = true,
    val largeText: Boolean = false,
    val animation: AnimationMode = AnimationMode.FULL,
    // Accessibility
    val highContrast: Boolean = false,
    val iconLabels: Boolean = false,
    val largeTouchTargets: Boolean = false,
    // Security & privacy
    /**
     * Default ON because this is what the app has always done: a tool's sensitive parameters are
     * never remembered, so selecting another tool starts them empty. OFF keeps them for the
     * session only (in memory, never on disk), so switching back does not mean retyping.
     */
    val clearSecretsOnToolSwitch: Boolean = true,
    val clearSecretsOnBackground: Boolean = false,
    val confirmPrivateKeyCopy: Boolean = true,
    val hidePrivateKeyPreview: Boolean = true,
    val sensitiveWarnings: Boolean = true,
    // Advanced
    val showProcessingTime: Boolean = false,
    val showToolId: Boolean = false,
    val showDetectionDetails: Boolean = false,
    val showValidationDetails: Boolean = false,
) {
    /** "Reduce animations" as one switch over the animation mode (see the class note). */
    val reduceAnimations: Boolean get() = animation != AnimationMode.FULL

    fun withReduceAnimations(reduce: Boolean): UiSettings = copy(
        animation = when {
            reduce && animation == AnimationMode.FULL -> AnimationMode.REDUCED
            !reduce -> AnimationMode.FULL
            else -> animation
        },
    )

    fun writeTo(data: PrefsData): PrefsData = data
        .with(PrefsKeys.DYNAMIC_COLOR, dynamicColor.toString())
        .with(PrefsKeys.LAYOUT_DENSITY, density.id)
        .with(PrefsKeys.SHOW_TOOL_ICONS, showToolIcons.toString())
        .with(PrefsKeys.MONOSPACE_OUTPUT, monospaceOutput.toString())
        .with(PrefsKeys.LARGE_TEXT, largeText.toString())
        .with(PrefsKeys.UI_ANIMATION, animation.id)
        .with(PrefsKeys.HIGH_CONTRAST, highContrast.toString())
        .with(PrefsKeys.ICON_LABELS, iconLabels.toString())
        .with(PrefsKeys.LARGE_TOUCH_TARGETS, largeTouchTargets.toString())
        .with(PrefsKeys.CLEAR_SECRETS_ON_TOOL_SWITCH, clearSecretsOnToolSwitch.toString())
        .with(PrefsKeys.CLEAR_SECRETS_ON_BACKGROUND, clearSecretsOnBackground.toString())
        .with(PrefsKeys.CONFIRM_PRIVATE_KEY_COPY, confirmPrivateKeyCopy.toString())
        .with(PrefsKeys.HIDE_PRIVATE_KEY_PREVIEW, hidePrivateKeyPreview.toString())
        .with(PrefsKeys.SENSITIVE_WARNINGS, sensitiveWarnings.toString())
        .with(PrefsKeys.SHOW_PROCESSING_TIME, showProcessingTime.toString())
        .with(PrefsKeys.SHOW_TOOL_ID, showToolId.toString())
        .with(PrefsKeys.SHOW_DETECTION_DETAILS, showDetectionDetails.toString())
        .with(PrefsKeys.SHOW_VALIDATION_DETAILS, showValidationDetails.toString())

    companion object {
        val DEFAULT = UiSettings()

        /** Every key this value owns, so the "kept when clearing temporary data" list stays complete. */
        val KEYS: List<String> = listOf(
            PrefsKeys.DYNAMIC_COLOR, PrefsKeys.LAYOUT_DENSITY, PrefsKeys.SHOW_TOOL_ICONS,
            PrefsKeys.MONOSPACE_OUTPUT, PrefsKeys.LARGE_TEXT, PrefsKeys.UI_ANIMATION,
            PrefsKeys.HIGH_CONTRAST, PrefsKeys.ICON_LABELS, PrefsKeys.LARGE_TOUCH_TARGETS,
            PrefsKeys.CLEAR_SECRETS_ON_TOOL_SWITCH, PrefsKeys.CLEAR_SECRETS_ON_BACKGROUND,
            PrefsKeys.CONFIRM_PRIVATE_KEY_COPY, PrefsKeys.HIDE_PRIVATE_KEY_PREVIEW,
            PrefsKeys.SENSITIVE_WARNINGS, PrefsKeys.SHOW_PROCESSING_TIME, PrefsKeys.SHOW_TOOL_ID,
            PrefsKeys.SHOW_DETECTION_DETAILS, PrefsKeys.SHOW_VALIDATION_DETAILS,
        )

        fun from(data: PrefsData): UiSettings {
            val d = DEFAULT
            return UiSettings(
                dynamicColor = data.bool(PrefsKeys.DYNAMIC_COLOR, d.dynamicColor),
                density = LayoutDensity.fromId(data.string(PrefsKeys.LAYOUT_DENSITY)),
                showToolIcons = data.bool(PrefsKeys.SHOW_TOOL_ICONS, d.showToolIcons),
                monospaceOutput = data.bool(PrefsKeys.MONOSPACE_OUTPUT, d.monospaceOutput),
                largeText = data.bool(PrefsKeys.LARGE_TEXT, d.largeText),
                animation = AnimationMode.fromId(data.string(PrefsKeys.UI_ANIMATION)),
                highContrast = data.bool(PrefsKeys.HIGH_CONTRAST, d.highContrast),
                iconLabels = data.bool(PrefsKeys.ICON_LABELS, d.iconLabels),
                largeTouchTargets = data.bool(PrefsKeys.LARGE_TOUCH_TARGETS, d.largeTouchTargets),
                clearSecretsOnToolSwitch = data.bool(PrefsKeys.CLEAR_SECRETS_ON_TOOL_SWITCH, d.clearSecretsOnToolSwitch),
                clearSecretsOnBackground = data.bool(PrefsKeys.CLEAR_SECRETS_ON_BACKGROUND, d.clearSecretsOnBackground),
                confirmPrivateKeyCopy = data.bool(PrefsKeys.CONFIRM_PRIVATE_KEY_COPY, d.confirmPrivateKeyCopy),
                hidePrivateKeyPreview = data.bool(PrefsKeys.HIDE_PRIVATE_KEY_PREVIEW, d.hidePrivateKeyPreview),
                sensitiveWarnings = data.bool(PrefsKeys.SENSITIVE_WARNINGS, d.sensitiveWarnings),
                showProcessingTime = data.bool(PrefsKeys.SHOW_PROCESSING_TIME, d.showProcessingTime),
                showToolId = data.bool(PrefsKeys.SHOW_TOOL_ID, d.showToolId),
                showDetectionDetails = data.bool(PrefsKeys.SHOW_DETECTION_DETAILS, d.showDetectionDetails),
                showValidationDetails = data.bool(PrefsKeys.SHOW_VALIDATION_DETAILS, d.showValidationDetails),
            )
        }
    }
}
