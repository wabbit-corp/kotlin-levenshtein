package one.wabbit.levenshtein

/**
 * Costs for Damerau–Levenshtein edits.
 * In many references, transposition cost is set to 1,
 * but you can configure it here as you like.
 */
data class DamerauEditCosts(
    val insertion: Int = 1,
    val deletion: Int = 1,
    val replacement: Int = 1,
    val transposition: Int = 1
)

/** Edit operations for Damerau–Levenshtein. */
enum class DamerauEditOp {
    MATCH,
    REPLACE,
    INSERT,
    DELETE,
    TRANSPOSITION
}

/** A single step in the Damerau–Levenshtein backtrack. */
data class DamerauEditStep(
    val op: DamerauEditOp,
    val lhsIndex: Int,     // Index in LHS to which this op applies
    val rhsIndex: Int      // Index in RHS to which this op applies
)

/**
 * A "common" Damerau–Levenshtein.
 *
 * @param lhsSize  length of LHS
 * @param rhsSize  length of RHS
 * @param isEqual  (i,j) -> true if lhs[i] == rhs[j] "enough" (case-insensitive?), else false
 * @param costs    insertion, deletion, replacement, transposition
 * @return minimum edit distance
 */
inline fun damerauLevenshteinCommon(
    lhsSize: Int,
    rhsSize: Int,
    crossinline isEqual: (Int, Int) -> Boolean,
    costs: DamerauEditCosts = DamerauEditCosts()
): Int {
    if (lhsSize == 0) return rhsSize * costs.insertion
    if (rhsSize == 0) return lhsSize * costs.deletion

    // dp[i][j] = min cost to convert lhs[0..i-1] to rhs[0..j-1]
    val dp = Array(lhsSize + 1) { IntArray(rhsSize + 1) { 0 } }

    // Init first row/col
    for (i in 1..lhsSize) {
        dp[i][0] = i * costs.deletion
    }
    for (j in 1..rhsSize) {
        dp[0][j] = j * costs.insertion
    }

    for (i in 1..lhsSize) {
        for (j in 1..rhsSize) {
            val matchCost = if (isEqual(i - 1, j - 1)) 0 else costs.replacement
            val del = dp[i - 1][j] + costs.deletion
            val ins = dp[i][j - 1] + costs.insertion
            val rep = dp[i - 1][j - 1] + matchCost

            var minCost = minOf(del, minOf(ins, rep))

            // Damerau transposition check:
            // i>1, j>1, and the crossing characters match
            if (i > 1 && j > 1) {
                // We'll see if lhs[i-1] == rhs[j-2] and lhs[i-2] == rhs[j-1]
                // If so, consider dp[i-2][j-2] + transposition cost
                // This "swaps" the two adjacent chars in LHS (or equivalently in RHS).
                // cost can differ from 1 if you want custom cost for transposition
                minCost = if (
                    isEqual(i - 1, j - 2) &&
                    isEqual(i - 2, j - 1)
                ) {
                    minOf(minCost, dp[i - 2][j - 2] + costs.transposition)
                } else {
                    minCost
                }
            }

            dp[i][j] = minCost
        }
    }
    return dp[lhsSize][rhsSize]
}

/**
 * Public overload for CharSequence with optional ignoreCase.
 */
fun damerauLevenshteinDistance(
    lhs: CharSequence,
    rhs: CharSequence,
    ignoreCase: Boolean = false,
    costs: DamerauEditCosts = DamerauEditCosts()
): Int {
    return damerauLevenshteinCommon(
        lhs.length,
        rhs.length,
        isEqual = { i, j ->
            if (ignoreCase) {
                lhs[i].lowercaseChar() == rhs[j].lowercaseChar()
            } else {
                lhs[i] == rhs[j]
            }
        },
        costs
    )
}

/**
 * Damerau–Levenshtein with path reconstruction.
 *
 * @return Pair(distance, list of edit steps)
 */
fun damerauLevenshteinWithPath(
    lhs: CharSequence,
    rhs: CharSequence,
    ignoreCase: Boolean = false,
    costs: DamerauEditCosts = DamerauEditCosts()
): Pair<Int, List<DamerauEditStep>> {
    val n = lhs.length
    val m = rhs.length
    if (n == 0 && m == 0) return 0 to emptyList()
    if (n == 0) {
        // purely insert all of rhs
        return (m * costs.insertion) to List(m) { idx ->
            DamerauEditStep(DamerauEditOp.INSERT, 0, idx)
        }
    }
    if (m == 0) {
        // purely delete all of lhs
        return (n * costs.deletion) to List(n) { idx ->
            DamerauEditStep(DamerauEditOp.DELETE, idx, 0)
        }
    }

    fun eq(i: Int, j: Int): Boolean {
        return if (ignoreCase) {
            lhs[i].lowercaseChar() == rhs[j].lowercaseChar()
        } else {
            lhs[i] == rhs[j]
        }
    }

    // dp & op tables
    val dp = Array(n + 1) { IntArray(m + 1) }
    val ops = Array(n + 1) { Array<DamerauEditOp?>(m + 1) { null } }

    // Init
    for (i in 1..n) {
        dp[i][0] = i * costs.deletion
        ops[i][0] = DamerauEditOp.DELETE
    }
    for (j in 1..m) {
        dp[0][j] = j * costs.insertion
        ops[0][j] = DamerauEditOp.INSERT
    }

    for (i in 1..n) {
        for (j in 1..m) {
            val costMatchOrReplace = if (eq(i - 1, j - 1)) 0 else costs.replacement
            val deleteCost = dp[i - 1][j] + costs.deletion
            val insertCost = dp[i][j - 1] + costs.insertion
            val replaceCost = dp[i - 1][j - 1] + costMatchOrReplace

            var bestCost = deleteCost
            var bestOp = DamerauEditOp.DELETE

            if (insertCost < bestCost) {
                bestCost = insertCost
                bestOp = DamerauEditOp.INSERT
            }
            if (replaceCost < bestCost) {
                bestCost = replaceCost
                bestOp = if (costMatchOrReplace == 0) DamerauEditOp.MATCH else DamerauEditOp.REPLACE
            }

            // Check transposition
            if (i > 1 && j > 1 && eq(i - 1, j - 2) && eq(i - 2, j - 1)) {
                val transpCost = dp[i - 2][j - 2] + costs.transposition
                if (transpCost < bestCost) {
                    bestCost = transpCost
                    bestOp = DamerauEditOp.TRANSPOSITION
                }
            }

            dp[i][j] = bestCost
            ops[i][j] = bestOp
        }
    }

    // Backtrack
    val steps = mutableListOf<DamerauEditStep>()
    var i = n
    var j = m
    while (i > 0 || j > 0) {
        val op = ops[i][j] ?: error("No operation stored at ($i,$j)")
        when (op) {
            DamerauEditOp.DELETE -> {
                steps.add(DamerauEditStep(DamerauEditOp.DELETE, i - 1, j))
                i -= 1
            }
            DamerauEditOp.INSERT -> {
                steps.add(DamerauEditStep(DamerauEditOp.INSERT, i, j - 1))
                j -= 1
            }
            DamerauEditOp.REPLACE -> {
                steps.add(DamerauEditStep(DamerauEditOp.REPLACE, i - 1, j - 1))
                i -= 1
                j -= 1
            }
            DamerauEditOp.MATCH -> {
                steps.add(DamerauEditStep(DamerauEditOp.MATCH, i - 1, j - 1))
                i -= 1
                j -= 1
            }
            DamerauEditOp.TRANSPOSITION -> {
                // This operation implies a swap of s1[i-2..i-1]
                steps.add(DamerauEditStep(DamerauEditOp.TRANSPOSITION, i - 2, j - 2))
                i -= 2
                j -= 2
            }
        }
    }
    steps.reverse()
    return dp[n][m] to steps
}