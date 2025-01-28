package one.wabbit.levenshtein

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class LevenshteinSpec {

    @Test
    fun testEmptyStrings() {
        assertEquals(0, levenshtein("", ""), "Both empty => distance = 0")
        assertEquals(3, levenshtein("", "abc"), "lhs empty => distance = length(rhs) = 3")
        assertEquals(4, levenshtein("abcd", ""), "rhs empty => distance = length(lhs) = 4")
    }

    @Test
    fun testIdenticalStrings() {
        assertEquals(0, levenshtein("test", "test"), "Identical strings => distance = 0")
        assertEquals(0, levenshtein(listOf(1, 2, 3), listOf(1, 2, 3)), "Identical lists => distance = 0")
    }

    @Test
    fun testBasicStringDistance() {
        // "kitten" -> "sitting" => classic example: distance = 3
        // typically operations: replace 'k'->'s', replace 'e'->'i', insert 'g' at end
        assertEquals(3, levenshtein("kitten", "sitting"))
    }

    @Test
    fun testBasicListDistance() {
        // 1,2,3 -> 2,2,4
        // possible transformations:
        // 1 -> 2 (replace cost=1), 3 -> 4 (replace cost=1), so total 2 if we do it that way
        val lhs = listOf(1, 2, 3)
        val rhs = listOf(2, 2, 4)
        val distance = levenshtein(lhs, rhs)
        // Let's do a quick manual check:
        //   1,2,3 -> 2,2,3 (replace 1->2) = +1
        //           2,2,3 -> 2,2,4 (replace 3->4) = +1
        // total = 2
        assertEquals(2, distance)
    }

    @Test
    fun testIgnoreCase() {
        // "ABC" vs "abc" => distance=0 if ignoreCase = true
        assertEquals(0, levenshtein("ABC", "abc", ignoreCase = true))
        // "ABC" vs "abc" => distance=3 if ignoreCase = false (all different)
        assertEquals(3, levenshtein("ABC", "abc", ignoreCase = false))
    }

    @Test
    fun testCustomCosts() {
        // "abc" -> "abc" with high replacement cost
        val highReplaceCost = EditCosts(insertion = 1, deletion = 1, replacement = 10)
        // no changes => distance=0
        assertEquals(0, levenshtein("abc", "abc", cost = highReplaceCost))

        // "abc" -> "abd" => only difference is c->d
        // with replacement cost=10, it might be cheaper to delete 'c' and insert 'd' if insertion + deletion < 10
        // default insertion=1, deletion=1 => total = 2 is cheaper than 10
        val dist = levenshtein("abc", "abd", cost = highReplaceCost)
        assertEquals(2, dist, "Cheaper to delete + insert than to replace if replacement=10")

        // "abc" -> "ab" => cost = 1 deletion if default
        // but if deletion cost=5, total=5
        val highDeleteCost = EditCosts(insertion = 1, deletion = 5, replacement = 1)
        assertEquals(5, levenshtein("abc", "ab", cost = highDeleteCost))
    }

    @Test
    fun testPathReconstructionStrings() {
        val (distance, path) = levenshteinWithPath("kitten", "sitting")
        assertEquals(3, distance, "kitten -> sitting distance=3")

        // We'll do a quick check of path validity.
        // Typical transformation: k->s, e->i, append g
        // But let's just confirm we have 3 changes in total
        val actualEdits = path.count { it.operation != EditOperation.MATCH }
        assertEquals(3, actualEdits)
    }

    @Test
    fun testPathReconstructionLists() {
        val lhs = listOf(1, 2, 3)
        val rhs = listOf(2, 2, 4)
        val (dist, path) = levenshteinWithPath(
            lhs,
            rhs,
            isEqual = { i, j -> lhs[i] == rhs[j] }
        )
        assertEquals(2, dist, "Distance = 2 as reasoned earlier")
        // Let's do a light verification that we have 2 non-MATCH operations
        val actualEdits = path.count { it.operation != EditOperation.MATCH }
        assertEquals(2, actualEdits)
    }

    @Test
    fun testEmptyToNonEmptyPath() {
        val (distance, path) = levenshteinWithPath("", "abc")
        assertEquals(3, distance, "All inserted => distance=3")
        // Expect 3 insertion operations
        assertTrue(path.all { it.operation == EditOperation.INSERT })
        assertEquals(3, path.size)
    }

    @Test
    fun testNonEmptyToEmptyPath() {
        val (distance, path) = levenshteinWithPath("abc", "")
        assertEquals(3, distance, "All deletions => distance=3")
        // Expect 3 deletion operations
        assertTrue(path.all { it.operation == EditOperation.DELETE })
        assertEquals(3, path.size)
    }

    @Test
    fun testMixedEditsPath() {
        // One example with custom costs, ignoring case
        val lhs = "Abcd"
        val rhs = "AxdE"
        val custom = EditCosts(insertion = 2, deletion = 2, replacement = 3)
        val (dist, path) = levenshteinWithPath(lhs, rhs, ignoreCase = true, cost = custom)
        assertEquals(7, dist)
        assertEquals(1, path.count { it.operation == EditOperation.REPLACE })
    }

    @Test
    fun testComplexReplacementCosts() {
        // "abc" -> "xyz" with custom costs
        val cost = EditCosts(insertion = 1, deletion = 1, replacement = 5)
        // Possibly cheaper to delete + insert (2 steps) rather than replace=5
        // "abc" -> "xyz" (3 letters to 3 letters)
        // Each letter is different => replace each char would be cost=15
        // But if for each char we do: delete + insert => cost=2 for each => total=6
        val dist = levenshtein("abc", "xyz", cost = cost)
        assertEquals(6, dist)
    }

    @Test
    fun testAllMatchesPath() {
        // "abc" -> "abc" => distance=0, everything is MATCH
        val (dist, path) = levenshteinWithPath("abc", "abc")
        assertEquals(0, dist)
        // Expect exactly 3 matches, no other ops
        assertEquals(3, path.size)
        path.forEach { step ->
            assertEquals(EditOperation.MATCH, step.operation)
        }
    }
}