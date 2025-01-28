package one.wabbit.levenshtein

/**
 * Allows you to specify different costs for insertion, deletion, and replacement.
 * By default, all costs = 1, matching classic Levenshtein distance.
 */
data class EditCosts(
    val insertion: Int = 1,
    val deletion: Int = 1,
    val replacement: Int = 1
)

/**
 * Enum representing possible edit operations during Levenshtein distance calculation.
 */
enum class EditOperation {
    MATCH,
    REPLACE,
    INSERT,
    DELETE
}

/**
 * Useful class to store a single edit step during reconstruction.
 *
 * @property operation One of MATCH, REPLACE, INSERT, DELETE
 * @property lhsIndex  The index in the lhs sequence that this operation refers to
 * @property rhsIndex  The index in the rhs sequence that this operation refers to
 */
data class EditStep(
    val operation: EditOperation,
    val lhsIndex: Int,
    val rhsIndex: Int
)

/**
 * A “common” Levenshtein distance function using the rolling-array technique,
 * parameterized by the sequence-index test for equality and by the EditCosts.
 *
 * @param lhsSize    Number of elements in lhs
 * @param rhsSize    Number of elements in rhs
 * @param isEqual    A function that returns true if lhs[i] and rhs[j] are “equal”
 * @param cost       The EditCosts for insertion, deletion, replacement
 * @return The computed Levenshtein distance
 */
inline fun levenshteinCommon(
    lhsSize: Int,
    rhsSize: Int,
    cost: EditCosts = EditCosts(),
    crossinline isEqual: (Int, Int) -> Boolean
): Int {
    // Quick checks for empty/equal sequences
    if (lhsSize == 0 && rhsSize == 0) return 0
    if (lhsSize == 0) return rhsSize * cost.insertion
    if (rhsSize == 0) return lhsSize * cost.deletion

    // Rolling arrays
    val prevRow = IntArray(lhsSize + 1) { it * cost.deletion }
    val currRow = IntArray(lhsSize + 1) { 0 }

    // Fill row by row
    for (i in 1..rhsSize) {
        // i in DP represents how many elements of rhs are considered
        currRow[0] = i * cost.insertion

        for (j in 1..lhsSize) {
            // j in DP represents how many elements of lhs are considered
            val matchOrReplaceCost = if (isEqual(j - 1, i - 1)) 0 else cost.replacement

            val costReplace = prevRow[j - 1] + matchOrReplaceCost
            val costInsert  = prevRow[j]     + cost.insertion
            val costDelete  = currRow[j - 1] + cost.deletion

            currRow[j] = minOf(costReplace, minOf(costInsert, costDelete))
        }

        // Swap references
        for (k in 0..lhsSize) {
            prevRow[k] = currRow[k]
        }
    }

    return prevRow[lhsSize]
}

/**
 * Levenshtein distance for CharSequences with optional case-insensitivity.
 *
 * @param lhs        The left-hand side CharSequence
 * @param rhs        The right-hand side CharSequence
 * @param ignoreCase If true, compare characters in a case-insensitive manner
 * @param cost       EditCosts
 * @return The computed Levenshtein distance
 */
fun levenshtein(
    lhs: CharSequence,
    rhs: CharSequence,
    ignoreCase: Boolean = false,
    cost: EditCosts = EditCosts()
): Int {
    return levenshteinCommon(
        lhs.length,
        rhs.length,
        cost = cost
    ) { i, j ->
        if (ignoreCase) {
            lhs[i].lowercaseChar() == rhs[j].lowercaseChar()
        } else {
            lhs[i] == rhs[j]
        }
    }
}

/**
 * Levenshtein distance for arbitrary lists of E.
 *
 * @param lhs   The left-hand side list
 * @param rhs   The right-hand side list
 * @param cost  EditCosts
 * @return The computed Levenshtein distance
 */
fun <E> levenshtein(
    lhs: List<E>,
    rhs: List<E>,
    cost: EditCosts = EditCosts()
): Int {
    return levenshteinCommon(
        lhsSize = lhs.size,
        rhsSize = rhs.size,
        cost = cost
    ) { i, j -> lhs[i] == rhs[j] }
}

/**
 * Reconstruct the path (sequence of edits) alongside computing the Levenshtein distance.
 * This uses a full 2D DP matrix for the cost, plus a 2D matrix for storing the operation.
 *
 * @param lhs      Left-hand side list/array
 * @param rhs      Right-hand side list/array
 * @param isEqual  Equality check
 * @param cost     Edit costs
 * @return A pair (distance, listOfEditSteps)
 */
fun <T> levenshteinWithPath(
    lhs: List<T>,
    rhs: List<T>,
    cost: EditCosts = EditCosts(),
    isEqual: (Int, Int) -> Boolean
): Pair<Int, List<EditStep>> {
    val n = lhs.size
    val m = rhs.size

    // DP table of distances
    val dp = Array(m + 1) { IntArray(n + 1) { 0 } }
    // DP table for operations: for each (i, j), store which operation led to optimal cost
    val op = Array(m + 1) { Array<EditOperation?>(n + 1) { null } }

    // Initialize first row/column
    for (j in 1..n) {
        dp[0][j] = dp[0][j - 1] + cost.deletion
        op[0][j] = EditOperation.DELETE
    }
    for (i in 1..m) {
        dp[i][0] = dp[i - 1][0] + cost.insertion
        op[i][0] = EditOperation.INSERT
    }

    // Fill DP
    for (i in 1..m) {
        for (j in 1..n) {
            val matchOrReplace = if (isEqual(j - 1, i - 1)) 0 else cost.replacement

            val delCost    = dp[i][j - 1] + cost.deletion
            val insCost    = dp[i - 1][j] + cost.insertion
            val replCost   = dp[i - 1][j - 1] + matchOrReplace

            // Choose minimum among delete, insert, replace
            val minVal = minOf(delCost, minOf(insCost, replCost))
            dp[i][j] = minVal

            when (minVal) {
                delCost  -> op[i][j] = EditOperation.DELETE
                insCost  -> op[i][j] = EditOperation.INSERT
                replCost -> {
                    op[i][j] = if (matchOrReplace == 0) EditOperation.MATCH
                    else EditOperation.REPLACE
                }
            }
        }
    }

    // Backtrack to get the sequence of operations
    val path = mutableListOf<EditStep>()
    var i = m
    var j = n
    while (i > 0 || j > 0) {
        val operation = op[i][j]
        when (operation) {
            EditOperation.DELETE -> {
                path.add(EditStep(EditOperation.DELETE, j - 1, i))
                // Move left in DP
                j--
            }
            EditOperation.INSERT -> {
                path.add(EditStep(EditOperation.INSERT, j, i - 1))
                // Move up in DP
                i--
            }
            EditOperation.REPLACE -> {
                path.add(EditStep(EditOperation.REPLACE, j - 1, i - 1))
                i--
                j--
            }
            EditOperation.MATCH -> {
                path.add(EditStep(EditOperation.MATCH, j - 1, i - 1))
                i--
                j--
            }
            else -> {
                // Should never happen if the DP table is fully filled
                throw IllegalStateException("No operation recorded at dp[$i][$j]")
            }
        }
    }
    path.reverse() // The backtrack yields the edits from end -> beginning

    val distance = dp[m][n]
    return distance to path
}

/**
 * Overload for CharSequence with path reconstruction.
 */
fun levenshteinWithPath(
    lhs: CharSequence,
    rhs: CharSequence,
    ignoreCase: Boolean = false,
    cost: EditCosts = EditCosts()
): Pair<Int, List<EditStep>> {
    // We'll convert CharSequence -> List<Char> for convenience
    val lhsList = lhs.toList()
    val rhsList = rhs.toList()
    return levenshteinWithPath(
        lhs = lhsList,
        rhs = rhsList,
        cost = cost,
        { i, j ->
            if (ignoreCase) {
                lhsList[i].lowercaseChar() == rhsList[j].lowercaseChar()
            } else {
                lhsList[i] == rhsList[j]
            }
        }
    )
}