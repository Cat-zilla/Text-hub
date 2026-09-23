package com.texthub.core

import com.texthub.core.prefs.PrefsData
import com.texthub.core.prefs.PrefsKeys
import com.texthub.core.prefs.formatDataSize
import com.texthub.core.prefs.moveFavoriteBefore
import com.texthub.core.prefs.moveFavoriteInDisplayedOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored-preferences model: favourites keep their order, "Clear temporary data" keeps them,
 * and only disposable data is ever removed.
 */
class PrefsModelTest {

    private fun data(vararg pairs: Pair<String, String>) = PrefsData(mapOf(*pairs))

    // ---------------------------------------------------------------- clear temporary data

    @Test fun clearingTemporaryDataKeepsFavourites() {
        val before = data(
            PrefsKeys.FAVORITES_ORDER to "aes|base64|sha256",
            PrefsKeys.RECENTS to "aes|base64",
            "${PrefsKeys.PARAMS_PREFIX}aes" to "keySize=128;format=openssl",
            "${PrefsKeys.PARAMS_PREFIX}base64" to "variant=url",
            PrefsKeys.THEME to "AMOLED",
            PrefsKeys.ACCENT to "TEAL",
            PrefsKeys.LAST_TOOL to "aes",
        )
        val after = before.clearTemporary()

        assertEquals(
            "favourites must survive a clear",
            listOf("aes", "base64", "sha256"),
            after.favorites(),
        )
        assertTrue("recents are temporary", after.recents().isEmpty())
        assertTrue("remembered parameters are temporary", after.paramsFor("aes").isEmpty())
        assertTrue(after.paramsFor("base64").isEmpty())
        assertEquals("theme", "AMOLED", after.string(PrefsKeys.THEME))
        assertEquals("accent", "TEAL", after.string(PrefsKeys.ACCENT))
        assertEquals("selected tool", "aes", after.string(PrefsKeys.LAST_TOOL))
    }

    @Test fun clearingRemovesNothingButTemporaryData() {
        val before = data(
            PrefsKeys.FAVORITES_ORDER to "aes|base64",
            PrefsKeys.RECENTS to "aes",
            "${PrefsKeys.PARAMS_PREFIX}aes" to "keySize=256",
            PrefsKeys.AUTO_PROCESS to "false",
            PrefsKeys.COPY_CONFIRMATION to "true",
        )
        val after = before.clearTemporary()
        val removed = before.entries.keys - after.entries.keys
        assertEquals(
            "only params + recents may be removed, not ${removed}",
            setOf(PrefsKeys.RECENTS, "${PrefsKeys.PARAMS_PREFIX}aes"),
            removed,
        )
    }

    @Test fun theReportedSizeDropsToZeroAfterClearingAndUsesReadableUnits() {
        val before = data(
            PrefsKeys.FAVORITES_ORDER to "aes|base64|chacha",
            "${PrefsKeys.PARAMS_PREFIX}aes" to "keySize=256".padEnd(1200, 'x'),
            PrefsKeys.RECENTS to "aes|base64|chacha|sha256",
        )
        assertTrue("size must be reported while data is held", before.temporaryBytes() > 800)
        assertTrue(formatDataSize(before.temporaryBytes()).endsWith("KB"))
        val after = before.clearTemporary()
        assertEquals("0 B", formatDataSize(after.temporaryBytes()))
        // The favourites are still there (they are not "temporary data").
        assertEquals(listOf("aes", "base64", "chacha"), after.favorites())
    }

    @Test fun sizesAreFormattedInSensibleUnits() {
        assertEquals("0 B", formatDataSize(0))
        assertEquals("512 B", formatDataSize(512))
        assertEquals("1.0 KB", formatDataSize(1024))
        assertEquals("1.5 KB", formatDataSize(1536))
        assertEquals("2.0 MB", formatDataSize(2L * 1024 * 1024))
    }

    // ---------------------------------------------------------------- favourites + ordering

    @Test fun favouritesKeepTheUserOrderAndPersistIt() {
        var store = PrefsData()
        store = store.toggleFavorite("aes")
        store = store.toggleFavorite("base64")
        store = store.toggleFavorite("sha256")
        assertEquals(listOf("aes", "base64", "sha256"), store.favorites())

        // Drag the last one to the top.
        store = store.moveFavorite(2, 0)
        assertEquals(listOf("sha256", "aes", "base64"), store.favorites())
        // ... and the new order is what a fresh read of the same store returns.
        assertEquals(listOf("sha256", "aes", "base64"), PrefsData(store.entries).favorites())

        // Drag the first one down by one.
        store = store.moveFavorite(0, 1)
        assertEquals(listOf("aes", "sha256", "base64"), store.favorites())

        // Removing one keeps the order of the rest.
        store = store.toggleFavorite("sha256")
        assertEquals(listOf("aes", "base64"), store.favorites())
    }

    @Test fun aDragPastTheEndsIsClampedInsteadOfCrashing() {
        val store = PrefsData().withFavorites(listOf("a", "b", "c"))
        // Dragging past the last row drops the item on the last row (never throws).
        assertEquals(listOf("b", "c", "a"), store.moveFavorite(0, 99).favorites())
        // Dragging above the first row drops it on the first row (or leaves it where it was).
        assertEquals(listOf("a", "b", "c"), store.moveFavorite(0, -5).favorites())
        assertEquals(listOf("c", "a", "b"), store.moveFavorite(2, -4).favorites())
        // An impossible source index changes nothing at all.
        assertEquals(listOf("a", "b", "c"), store.moveFavorite(7, 0).favorites())
    }

    @Test fun theOldUnorderedFavouritesAreMigratedWithoutLoss() {
        // 1.4.2 and earlier stored a StringSet under "favorites".
        val legacy = data(PrefsKeys.FAVORITES_LEGACY to "sha256|aes|base64")
        val order = listOf("base64", "aes", "sha256", "chacha")
        assertEquals(
            "the registry order is used once, and nothing is dropped",
            listOf("base64", "aes", "sha256"),
            legacy.favorites(order),
        )
        // Toggling rewrites it in the new ordered form and drops the legacy key.
        val upgraded = legacy.toggleFavorite("chacha", order)
        assertEquals(listOf("base64", "aes", "sha256", "chacha"), upgraded.favorites(order))
        assertFalse(upgraded.entries.containsKey(PrefsKeys.FAVORITES_LEGACY))
    }

    @Test fun aFreshInstallHasNoFavouritesAndNoTemporaryData() {
        val empty = PrefsData()
        assertTrue(empty.favorites().isEmpty())
        assertTrue(empty.recents().isEmpty())
        assertEquals(0L, empty.temporaryBytes())
        assertTrue(empty.clearTemporary().entries.isEmpty())
    }

    // ---------------------------------------------------------------- recents + parameters

    @Test fun recentsAreNewestFirstAndCapped() {
        var store = PrefsData()
        listOf("a", "b", "c", "d", "e", "f", "g", "h").forEach { store = store.withRecent(it) }
        assertEquals(listOf("h", "g", "f", "e", "d", "c"), store.recents())
        // Re-using an old tool moves it back to the front instead of duplicating it.
        store = store.withRecent("f")
        assertEquals(listOf("f", "h", "g", "e", "d", "c"), store.recents())
    }

    @Test fun parametersRoundTripAndStayPerTool() {
        val store = PrefsData()
            .withParams("aes", mapOf("keySize" to "128", "format" to "openssl"))
            .withParams("base64", mapOf("variant" to "url"))
        assertEquals(mapOf("keySize" to "128", "format" to "openssl"), store.paramsFor("aes"))
        assertEquals(mapOf("variant" to "url"), store.paramsFor("base64"))
        assertTrue(store.paramsFor("chacha").isEmpty())
    }

    @Test fun aSensitiveValueIsNeverStoredByTheViewModelContract() {
        // The view model filters with ToolMeta before calling withParams; this asserts the model
        // itself never invents a secret and that a cleared store holds no values at all.
        val store = PrefsData().withParams("aes", mapOf("keySize" to "256"))
        assertEquals("256", store.paramsFor("aes")["keySize"])
        assertTrue(store.paramsFor("aes").keys.none { it == "password" || it == "key" })
        assertTrue(store.clearTemporary().paramsFor("aes").isEmpty())
    }

    // ---------------------------------------------------------------- drag stored by id

    @Test fun aDragIsStoredByIDSoFilteredFavouritesCannotShiftIt() {
        // The favourites of tools that no longer exist are skipped on screen, so the list the user
        // sees is a subsequence of the stored one: A C E while the store holds A B C D E.
        val stored = listOf("a", "b", "c", "d", "e")
        val displayed = listOf("a", "c", "e")

        // C is dragged to the bottom of the on-screen list: it becomes the last favourite.
        assertEquals(
            listOf("a", "b", "d", "e", "c"),
            moveFavoriteInDisplayedOrder(stored, displayed, "c", anchorId = null),
        )
        // E is dragged to the top.
        assertEquals(
            listOf("e", "a", "b", "c", "d"),
            moveFavoriteInDisplayedOrder(stored, displayed, "e", anchorId = "a"),
        )
    }

    @Test fun theSameDragThroughTheStoreKeepsFavouritesAndWritesTheOrder() {
        var store = PrefsData().withFavorites(listOf("a", "b", "c", "d", "e"))
        store = store.moveFavoriteBefore("e", "a")
        assertEquals(listOf("e", "a", "b", "c", "d"), store.favorites())
        // Re-read from the stored map: the order is on disk, not only in memory.
        assertEquals(listOf("e", "a", "b", "c", "d"), PrefsData(store.entries).favorites())

        // Dropping at the end uses a null anchor.
        store = store.moveFavoriteBefore("a", null)
        assertEquals(listOf("e", "b", "c", "d", "a"), store.favorites())
        // Empty temporary data must never touch the order.
        assertEquals(listOf("e", "b", "c", "d", "a"), PrefsData(store.entries).clearTemporary().favorites())
    }

    @Test fun aMoveThatChangesNothingDoesNotRewriteTheStore() {
        val store = PrefsData().withFavorites(listOf("a", "b", "c"))
        // Already in front of "b": nothing to do.
        assertEquals(store.entries, store.moveFavoriteBefore("a", "b").entries)
        // An id that is not stored, and an anchor that is not stored, change nothing either.
        assertEquals(store.entries, store.moveFavoriteBefore("zz", "a").entries)
        assertEquals(store.entries, store.moveFavoriteBefore("a", "zz").entries)
        assertEquals(listOf("a", "b", "c"), store.moveFavoriteBefore("a", "zz").favorites())
    }

    @Test fun anAnchorThatIsNotDisplayedIsRefusedInsteadOfMovingTheWrongEntry() {
        val stored = listOf("a", "b", "c", "d")
        // "c" is not part of what is on screen: the drop is refused, the order is untouched.
        assertEquals(stored, moveFavoriteInDisplayedOrder(stored, listOf("a", "b"), "a", anchorId = "c"))
        // A dragged row that is not on screen is refused too.
        assertEquals(stored, moveFavoriteInDisplayedOrder(stored, listOf("a", "b"), "d", anchorId = "a"))
        // And the plain move refuses an unknown anchor as well.
        assertEquals(stored, moveFavoriteBefore(stored, "a", "zz"))
    }
}
