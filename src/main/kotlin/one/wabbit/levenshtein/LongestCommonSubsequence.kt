package one.wabbit.levenshtein

/**
 * A "common" LCS function that returns the *length* of the LCS.
 *
 * @param lhsSize   length of LHS
 * @param rhsSize   length of RHS
 * @param isEqual   (i,j)-> whether lhs[i] == rhs[j] for LCS purposes
 * @return the length of LCS
 */
inline fun lcsCommon(
    lhsSize: Int,
    rhsSize: Int,
    crossinline isEqual: (Int, Int) -> Boolean
): Int {
    if (lhsSize == 0 || rhsSize == 0) return 0

    val dp = Array(lhsSize + 1) { IntArray(rhsSize + 1) }

    for (i in 1..lhsSize) {
        for (j in 1..rhsSize) {
            dp[i][j] = if (isEqual(i - 1, j - 1)) {
                dp[i - 1][j - 1] + 1
            } else {
                maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
    }
    return dp[lhsSize][rhsSize]
}

/**
 * Public overload for CharSequence, ignoring case if needed.
 */
fun longestCommonSubsequence(
    lhs: CharSequence,
    rhs: CharSequence,
    ignoreCase: Boolean = false
): Int {
    return lcsCommon(lhs.length, rhs.length,
        isEqual = { i, j ->
            if (ignoreCase)
                lhs[i].lowercaseChar() == rhs[j].lowercaseChar()
            else
                lhs[i] == rhs[j]
        }
    )
}

enum class LcsOp {
    MATCH,     // lhs[i-1] == rhs[j-1] used in LCS
    SKIP_LHS,  // we skip lhs[i-1]
    SKIP_RHS   // we skip rhs[j-1]
}

data class LcsStep(
    val op: LcsOp,
    val lhsIndex: Int,  // which index in lhs does this refer to
    val rhsIndex: Int   // which index in rhs
)

/**
 * A version of LCS that stores a path of operations: MATCH, SKIP_LHS, SKIP_RHS.
 *
 * @return Pair( lengthOfLCS, listOfOperations )
 *   Where 'listOfOperations' is from first to last in a backtrack sense (we'll reverse at the end).
 */
fun lcsWithPath(
    lhs: CharSequence,
    rhs: CharSequence,
    ignoreCase: Boolean = false
): Pair<Int, List<LcsStep>> {
    val n = lhs.length
    val m = rhs.length
    if (n == 0 || m == 0) {
        // LCS is 0, path is all skip from one or both strings
        return 0 to buildList {
            // purely skip LHS or skip RHS if you truly want the alignment,
            // but typically an empty list is enough to show no matches.
        }
    }

    // Equality check that respects ignoreCase if needed
    fun eq(i: Int, j: Int): Boolean {
        return if (ignoreCase) {
            lhs[i].lowercaseChar() == rhs[j].lowercaseChar()
        } else {
            lhs[i] == rhs[j]
        }
    }

    // dp[i][j] = length of LCS for lhs[0..i-1], rhs[0..j-1]
    val dp = Array(n + 1) { IntArray(m + 1) }
    // ops[i][j] = which operation was used to get dp[i][j]
    val ops = Array(n + 1) { Array<LcsOp?>(m + 1) { null } }

    // Fill DP
    for (i in 1..n) {
        for (j in 1..m) {
            if (eq(i - 1, j - 1)) {
                // match extends dp[i-1][j-1]
                dp[i][j] = dp[i - 1][j - 1] + 1
                ops[i][j] = LcsOp.MATCH
            } else {
                // skip either lhs or rhs, pick whichever yields a larger LCS
                if (dp[i - 1][j] >= dp[i][j - 1]) {
                    dp[i][j] = dp[i - 1][j]
                    ops[i][j] = LcsOp.SKIP_LHS
                } else {
                    dp[i][j] = dp[i][j - 1]
                    ops[i][j] = LcsOp.SKIP_RHS
                }
            }
        }
    }

    // Now backtrack to get the path
    val path = mutableListOf<LcsStep>()
    var i = n
    var j = m

    while (i > 0 && j > 0) {
        when (ops[i][j]) {
            LcsOp.MATCH -> {
                // We used lhs[i-1] == rhs[j-1]
                path.add(LcsStep(LcsOp.MATCH, i - 1, j - 1))
                i -= 1
                j -= 1
            }
            LcsOp.SKIP_LHS -> {
                path.add(LcsStep(LcsOp.SKIP_LHS, i - 1, j))
                i -= 1
            }
            LcsOp.SKIP_RHS -> {
                path.add(LcsStep(LcsOp.SKIP_RHS, i, j - 1))
                j -= 1
            }
            else -> {
                // In theory, ops[i][j] should never be null if i,j>0
                break
            }
        }
    }
    path.reverse()

    return dp[n][m] to path
}