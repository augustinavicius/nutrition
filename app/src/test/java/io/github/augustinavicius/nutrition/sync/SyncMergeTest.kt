package io.github.augustinavicius.nutrition.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private fun food(uid: String, name: String, at: Long) = SyncFood(
        uid = uid,
        name = name,
        source = "CUSTOM",
        per100g = SyncNutrients(100.0, 1.0, 2.0, 3.0),
        updatedAt = at,
    )

    private fun grave(uid: String, at: Long, kind: String = "FOOD") = SyncDeletion(kind, uid, at)

    private fun doc(
        foods: List<SyncFood> = emptyList(),
        deletions: List<SyncDeletion> = emptyList(),
    ) = SyncDocument(foods = foods, deletions = deletions)

    @Test
    fun `a first sync pulls everything the server has`() {
        val result = merge(local = doc(), remote = doc(listOf(food("a", "Apple", 10))), now = 99)
        assertEquals(listOf("Apple"), result.foodsToApply.map { it.name })
        assertEquals(1, result.merged.foods.size)
    }

    @Test
    fun `a first sync pushes everything the device has`() {
        val result = merge(local = doc(listOf(food("a", "Apple", 10))), remote = doc(), now = 99)
        assertTrue(result.foodsToApply.isEmpty())
        assertEquals(listOf("Apple"), result.merged.foods.map { it.name })
    }

    @Test
    fun `the newer edit wins, whichever side made it`() {
        val localNewer = merge(
            local = doc(listOf(food("a", "Local", 20))),
            remote = doc(listOf(food("a", "Remote", 10))),
            now = 99,
        )
        assertEquals(listOf("Local"), localNewer.merged.foods.map { it.name })
        assertTrue("nothing to pull when local already has the newer copy", localNewer.foodsToApply.isEmpty())

        val remoteNewer = merge(
            local = doc(listOf(food("a", "Local", 10))),
            remote = doc(listOf(food("a", "Remote", 20))),
            now = 99,
        )
        assertEquals(listOf("Remote"), remoteNewer.merged.foods.map { it.name })
        assertEquals(listOf("Remote"), remoteNewer.foodsToApply.map { it.name })
    }

    @Test
    fun `a deletion removes a record the other device still has`() {
        val result = merge(
            local = doc(listOf(food("a", "Apple", 10))),
            remote = doc(deletions = listOf(grave("a", 20))),
            now = 99,
        )
        assertTrue(result.merged.foods.isEmpty())
        assertEquals(listOf("a"), result.deletionsToApply.map { it.uid })
        assertEquals(listOf("a"), result.merged.deletions.map { it.uid })
    }

    @Test
    fun `re-adding a record after deleting it brings it back`() {
        val result = merge(
            local = doc(listOf(food("a", "Apple again", 30))),
            remote = doc(deletions = listOf(grave("a", 20))),
            now = 99,
        )
        assertEquals(listOf("Apple again"), result.merged.foods.map { it.name })
        assertTrue("the tombstone must not linger", result.merged.deletions.isEmpty())
        assertTrue(result.deletionsToApply.isEmpty())
    }

    @Test
    fun `a deletion both sides already know about needs no local work`() {
        val result = merge(
            local = doc(deletions = listOf(grave("a", 20))),
            remote = doc(deletions = listOf(grave("a", 20))),
            now = 99,
        )
        assertTrue(result.deletionsToApply.isEmpty())
        assertEquals(1, result.merged.deletions.size)
    }

    @Test
    fun `a locally deleted record stays deleted and is not pulled back`() {
        val result = merge(
            local = doc(deletions = listOf(grave("a", 20))),
            remote = doc(listOf(food("a", "Apple", 10))),
            now = 99,
        )
        assertTrue(result.merged.foods.isEmpty())
        assertTrue("already gone locally", result.deletionsToApply.isEmpty())
    }

    @Test
    fun `an unknown remote deletion is recorded so it cannot be undone later`() {
        val result = merge(local = doc(), remote = doc(deletions = listOf(grave("a", 20))), now = 99)
        assertEquals(listOf("a"), result.deletionsToApply.map { it.uid })
    }

    @Test
    fun `records the other side has never seen survive untouched`() {
        val result = merge(
            local = doc(listOf(food("a", "Apple", 10))),
            remote = doc(listOf(food("b", "Banana", 10))),
            now = 99,
        )
        assertEquals(listOf("Apple", "Banana"), result.merged.foods.map { it.name }.sorted())
        assertEquals(listOf("Banana"), result.foodsToApply.map { it.name })
    }

    @Test
    fun `simultaneous edits resolve the same way on both devices`() {
        val a = doc(listOf(food("x", "From A", 50)))
        val b = doc(listOf(food("x", "From B", 50)))
        // Same timestamp on both sides: whichever device syncs, the remote copy is kept, so
        // the two devices agree rather than flip-flopping.
        assertEquals("From B", merge(local = a, remote = b, now = 99).merged.foods.single().name)
        assertEquals("From A", merge(local = b, remote = a, now = 99).merged.foods.single().name)
    }

    @Test
    fun `merging is stable — a second pass changes nothing`() {
        val local = doc(listOf(food("a", "Apple", 10), food("b", "Banana", 30)))
        val remote = doc(listOf(food("a", "Apricot", 20)), deletions = listOf(grave("c", 5)))

        val first = merge(local, remote, now = 99)
        val second = merge(first.merged, first.merged, now = 100)

        assertEquals(first.merged.foods, second.merged.foods)
        assertEquals(first.merged.deletions, second.merged.deletions)
        assertTrue("a settled library has nothing left to apply", !second.hasLocalChanges)
    }

    @Test
    fun `recipes merge on the same rules as foods`() {
        val recipe = { uid: String, name: String, at: Long ->
            SyncRecipe(uid = uid, name = name, updatedAt = at)
        }
        val result = merge(
            local = SyncDocument(recipes = listOf(recipe("r", "Old chilli", 10))),
            remote = SyncDocument(
                recipes = listOf(recipe("r", "New chilli", 20)),
                deletions = listOf(grave("r2", 15, kind = "RECIPE")),
            ),
            now = 99,
        )
        assertEquals(listOf("New chilli"), result.merged.recipes.map { it.name })
        assertEquals(listOf("New chilli"), result.recipesToApply.map { it.name })
        assertEquals(listOf("r2"), result.merged.deletions.map { it.uid })
    }
}
