package com.texthub.app.ui.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.texthub.app.ui.settings.SettingsPage

/**
 * The one support action Text Hub offers, and the only two places it is ever shown: the root of
 * Settings and About. It is a native action, not an embedded button:
 *
 *  * **no WebView** - nothing of the page is rendered inside the app;
 *  * **no network code and no INTERNET permission** - the app never makes a request itself;
 *  * **no remote image** - the coffee glyph is a Material icon that ships inside the APK;
 *  * **no stored state** - whether the user tapped it, when, or from where is not recorded
 *    anywhere, and there is no analytics or telemetry in this app at all.
 *
 * Tapping it hands the URL to the user's normal browser with the standard
 * `ACTION_VIEW` intent, exactly like any other "open this link" action on Android. When no
 * installed app can handle it, [openSupportPage] returns false instead of throwing, and the
 * caller shows a short message.
 */
object SupportAction {

    /** The support page. Kept as a constant so a test can assert the exact URL. */
    const val URL: String = "https://www.buymeacoffee.com/Catzilla0"

    /**
     * The pages that show the support action, in the order they are defined. Exactly two, and
     * nowhere else - see [com.texthub.app.ui.settings.SupportPlacement].
     */
    val pages: List<SettingsPage> = com.texthub.app.ui.settings.SupportPlacement.pages

    /** True when [page] is one of the two allowed places. */
    fun isShownOn(page: SettingsPage): Boolean = page in pages
}

/**
 * Opens the support page in the user's normal external browser.
 *
 * @return true when an activity was started, false when nothing on the device can handle the
 *   intent (the caller then says so, quietly, instead of the app crashing).
 */
fun openSupportPage(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SupportAction.URL))
    // A plain view intent: no component, no package, no extras, no chooser restriction - the
    // system offers whatever browser the user already has, and Text Hub learns nothing about it.
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
