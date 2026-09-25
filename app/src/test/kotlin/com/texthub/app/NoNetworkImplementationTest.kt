package com.texthub.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Text Hub stays local-first: no INTERNET permission, no WebView, no network client, no remote
 * image loading, no analytics. These are the guarantees the 1.7.0 support action must not break -
 * it opens the user's browser and nothing else.
 *
 * The test reads the real sources from disk, so it fails the build the moment a network type or a
 * permission creeps in, wherever it is added.
 */
class NoNetworkImplementationTest {

    private fun projectRoot(): File {
        // Walk up from the working directory until the Gradle settings file is found.
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        // Fall back to the location of the compiled test class (app/build/classes/...).
        val codeSource = NoNetworkImplementationTest::class.java.protectionDomain?.codeSource?.location?.path
            ?: throw IllegalStateException("project root not found from " + File("").absolutePath)
        dir = File(codeSource).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        throw IllegalStateException("project root not found from " + File("").absolutePath)
    }

    private fun ktFiles(): List<File> {
        val root = projectRoot()
        return File(root, "app/src/main").walkTopDown().filter { it.extension == "kt" }.toList() +
            File(root, "core/src/main").walkTopDown().filter { it.extension == "kt" }.toList()
    }

    /**
     * Kotlin sources with comments and KDoc removed, so a comment that *says* "no WebView, no
     * analytics" does not read as a WebView or analytics reference. Only actual code counts.
     * String literals are walked over untouched, so a URL like "https://…" keeps its slashes.
     */
    private fun codeWithoutComments(file: File): String {
        val text = file.readText()
        val out = StringBuilder(text.length)
        var i = 0
        val n = text.length
        while (i < n) {
            when {
                // block comment
                text.startsWith("/*", i) -> {
                    val end = text.indexOf("*/", i + 2)
                    i = if (end < 0) n else end + 2
                    out.append(' ')
                }
                // line comment (only when not inside a string: a "//" inside a URL still counts)
                text.startsWith("//", i) -> {
                    var j = i
                    while (j < n && text[j] != '\n') j++
                    i = j
                    out.append('\n')
                }
                // string literal: keep verbatim
                text[i] == '"' -> {
                    var j = i + 1
                    while (j < n) {
                        if (text[j] == '\\') j += 2 else if (text[j] == '"') { j++; break } else j++
                    }
                    out.append(text, i, minOf(j, n))
                    i = j
                }
                // raw string
                text.startsWith("\"\"\"", i) -> {
                    val end = text.indexOf("\"\"\"", i + 3)
                    val j = if (end < 0) n else end + 3
                    out.append(text, i, j)
                    i = j
                }
                else -> {
                    out.append(text[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun manifest(): String =
        File(projectRoot(), "app/src/main/AndroidManifest.xml").readText()

    // ------------------------------------------------------------------- permissions

    @Test fun theManifestDeclaresNoInternetPermission() {
        val text = manifest()
        // The manifest carries an explicit tools:node="remove" for INTERNET, so even a library
        // that asked for it would be stripped.
        assertTrue(
            "INTERNET must be removed in the manifest",
            text.contains("android.permission.INTERNET") && text.contains("tools:node=\"remove\""),
        )
        // No other permission is requested either, beyond Android's own internal one.
        val requested = Regex("""<uses-permission\s+android:name="([^"]+)"""").findAll(text)
            .map { it.groupValues[1] }
            .filterNot { it == "android.permission.INTERNET" }
            .toList()
        assertTrue("unexpected permissions: $requested", requested.isEmpty())
    }

    // ---------------------------------------------------------------------- no WebView

    @Test fun noWebViewIsUsedAnywhere() {
        ktFiles().forEach { file ->
            val text = codeWithoutComments(file)
            listOf("WebView", "WebChromeClient", "WebViewClient", "WebSettings", "javascriptInterface").forEach { symbol ->
                assertFalse("${file.name} references $symbol", text.contains(symbol))
            }
        }
    }

    // ------------------------------------------------------------------- no networking

    @Test fun noNetworkClientOrSocketIsUsedAnywhere() {
        val forbidden = listOf(
            "java.net.HttpURLConnection", "java.net.URL(", "java.net.Socket", "java.net.URLConnection",
            "okhttp3", "retrofit2", "HttpClient", "ktor.client",
            "openConnection(", "URLEncoder",
        )
        ktFiles().forEach { file ->
            val text = codeWithoutComments(file)
            forbidden.forEach { symbol ->
                assertFalse("${file.name} references $symbol", text.contains(symbol))
            }
        }
    }

    @Test fun noRemoteImageOrConfigurationIsLoaded() {
        // No Coil/Glide/Picasso, no remote config, no analytics or crash reporter.
        val forbidden = listOf(
            "coil", " glide", "Glide", "picasso", "Picasso",
            "Firebase", "firebase", "Crashlytics", "Analytics", "analytics",
            "telemetry", "Telemetry",
        )
        ktFiles().forEach { file ->
            val text = codeWithoutComments(file)
            forbidden.forEach { symbol ->
                assertFalse("${file.name} references $symbol", text.contains(symbol))
            }
        }
    }

    @Test fun nothingIsLogged() {
        ktFiles().forEach { file ->
            val text = codeWithoutComments(file)
            listOf("android.util.Log", "Log.d(", "Log.e(", "Log.i(", "Log.w(", "println(", "printStackTrace").forEach { symbol ->
                assertFalse("${file.name} references $symbol", text.contains(symbol))
            }
        }
    }

    // ------------------------------------------------- the support action is browser-only

    @Test fun theSupportActionOnlyBuildsABrowserIntent() {
        val source = codeWithoutComments(File(projectRoot(), "app/src/main/kotlin/com/texthub/app/ui/support/SupportAction.kt"))
        assertTrue("it must open an ACTION_VIEW intent", source.contains("Intent.ACTION_VIEW"))
        assertTrue("it must hand the URL to the system", source.contains("Uri.parse"))
        // ...and it must do nothing else.
        listOf("WebView", "loadUrl", "HttpURLConnection", "okhttp", "Socket").forEach { symbol ->
            assertFalse("SupportAction.kt references $symbol", source.contains(symbol))
        }
        // The failure path is handled, not thrown.
        assertTrue(source.contains("ActivityNotFoundException"))
        // The URL is the one the specification asks for.
        assertTrue(source.contains("\"https://www.buymeacoffee.com/Catzilla0\""))
    }
}
