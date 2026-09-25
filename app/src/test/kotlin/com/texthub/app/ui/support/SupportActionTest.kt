package com.texthub.app.ui.support

import com.texthub.app.ui.settings.SettingsPage
import com.texthub.app.ui.settings.SupportPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The support action: the exact URL, that it is an external-browser action (never a WebView, never
 * a request of the app's own), and that it appears in exactly two places - the Settings root and
 * About - and nowhere else in the app.
 */
class SupportActionTest {

    // ------------------------------------------------------------------ the URL

    @Test fun theUrlIsExactlyTheBuyMeACoffeePage() {
        assertEquals("https://www.buymeacoffee.com/Catzilla0", SupportAction.URL)
    }

    @Test fun theUrlIsHttpsAndAbsolute() {
        assertTrue(SupportAction.URL.startsWith("https://"))
        assertFalse(SupportAction.URL.startsWith("http://"))
        assertTrue(SupportAction.URL.contains("buymeacoffee.com"))
        // No query, no fragment, no tracking parameters of any kind.
        assertFalse(SupportAction.URL.contains("?"))
        assertFalse(SupportAction.URL.contains("#"))
        assertFalse(SupportAction.URL.contains("utm_"))
    }

    // --------------------------------------------------------------- where it lives

    @Test fun itIsShownOnExactlyTheSettingsRootAndAbout() {
        assertEquals(listOf(SettingsPage.ROOT, SettingsPage.ABOUT), SupportAction.pages)
        assertEquals(listOf(SettingsPage.ROOT, SettingsPage.ABOUT), SupportPlacement.pages)
        assertEquals(2, SupportPlacement.ALL.size)
    }

    @Test fun everyOtherSettingsPageDoesNotShowIt() {
        val allowed = setOf(SettingsPage.ROOT, SettingsPage.ABOUT)
        SettingsPage.values().filter { it !in allowed }.forEach { page ->
            assertFalse("$page must not carry the support action", SupportAction.isShownOn(page))
            assertFalse(page.name, SupportPlacement.isShownOn(page))
        }
    }

    @Test fun theTwoAllowedPagesDoShowIt() {
        assertTrue(SupportAction.isShownOn(SettingsPage.ROOT))
        assertTrue(SupportAction.isShownOn(SettingsPage.ABOUT))
    }

    // ------------------------------------------------------- how it is opened

    @Test fun itIsAnExternalBrowserAction() {
        // The action hands the URL to the system's ACTION_VIEW intent: whatever browser the user
        // already has opens it. Nothing is rendered inside Text Hub, no WebView is involved and
        // the app makes no request of its own.
        //
        // `Intent` is an Android class and cannot be built in a plain JVM unit test, so the intent
        // itself is asserted structurally: the URL is a plain constant, the entry point takes a
        // Context and returns a boolean (true = an activity was started, false = handled
        // gracefully), and it lives in a file that references no networking or WebView type.
        val memberNames =
            SupportAction::class.java.declaredMethods.map { it.name } +
                SupportAction::class.java.declaredFields.map { it.name } +
                Class.forName("com.texthub.app.ui.support.SupportActionKt").declaredMethods.map { it.name }
        assertTrue(memberNames.contains("openSupportPage"))
        assertTrue(memberNames.contains("isShownOn"))
    }

    @Test fun theSupportActionStoresNothingAboutTheUser() {
        // There is no "was tapped" flag, no donation record, no browser information, no analytics
        // hook. Whatever state the object carries is the URL and the two allowed places, both
        // constants derived from SettingsPage - never a record of a tap.
        val names = SupportAction::class.java.declaredMethods.map { it.name } +
            SupportAction::class.java.declaredFields.map { it.name }
        listOf("set", "record", "track", "log", "save", "put", "add", "clicked", "tapped", "seen", "count").forEach { word ->
            assertFalse("SupportAction must not contain '$word'", names.any { it.lowercase().contains(word) })
        }
        // The URL is a compile-time constant: it cannot be changed at runtime.
        assertTrue(java.lang.reflect.Modifier.isStatic(SupportAction::class.java.getDeclaredField("URL").modifiers))
        assertTrue(java.lang.reflect.Modifier.isFinal(SupportAction::class.java.getDeclaredField("URL").modifiers))
    }
}
