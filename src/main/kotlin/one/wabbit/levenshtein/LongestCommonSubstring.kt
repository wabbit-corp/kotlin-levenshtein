package one.wabbit.levenshtein

/**
 * A "common" function for LCS *substring* (contiguous).
 *
 * dp[i][j] = if (isEqual(i-1,j-1)) then dp[i-1][j-1]+1 else 0
 * Keep track of maximum dp[i][j].
 */
inline fun longestCommonSubstringCommon(
    lhsSize: Int,
    rhsSize: Int,
    crossinline isEqual: (Int, Int) -> Boolean
): Int {
    if (lhsSize == 0 || rhsSize == 0) return 0
    val dp = Array(lhsSize + 1) { IntArray(rhsSize + 1) { 0 } }
    var maxLen = 0

    for (i in 1..lhsSize) {
        for (j in 1..rhsSize) {
            if (isEqual(i - 1, j - 1)) {
                dp[i][j] = dp[i - 1][j - 1] + 1
                if (dp[i][j] > maxLen) {
                    maxLen = dp[i][j]
                }
            } else {
                dp[i][j] = 0
            }
        }
    }
    return maxLen
}

fun longestCommonSubstring(
    lhs: CharSequence,
    rhs: CharSequence,
    ignoreCase: Boolean = false
): Int {
    return longestCommonSubstringCommon(lhs.length, rhs.length,
        isEqual = { i, j ->
            if (ignoreCase)
                lhs[i].lowercaseChar() == rhs[j].lowercaseChar()
            else
                lhs[i] == rhs[j]
        }
    )
}

/**
 * Data class to store the result of the Longest Common Substring,
 * including the substring itself and the start indices in LHS and RHS.
 */
data class LongestCommonSubstringResult(
    val length: Int,
    val substring: String,
    val lhsIndex: Int,
    val rhsIndex: Int
)

/**
 * Returns the length of the longest common substring plus the actual substring text.
 * Also provides the starting index in each string (lhs and rhs).
 *
 * @param lhs        The left-hand side CharSequence
 * @param rhs        The right-hand side CharSequence
 * @param ignoreCase Whether to treat the comparison as case-insensitive
 * @return A LongestCommonSubstringResult containing:
 *         - length of the substring
 *         - the common substring text
 *         - lhsIndex where it starts in lhs
 *         - rhsIndex where it starts in rhs
 */
fun longestCommonSubstringWithPath(
    lhs: CharSequence,
    rhs: CharSequence,
    ignoreCase: Boolean = false
): LongestCommonSubstringResult {
    val n = lhs.length
    val m = rhs.length
    if (n == 0 || m == 0) {
        return LongestCommonSubstringResult(
            length = 0,
            substring = "",
            lhsIndex = 0,
            rhsIndex = 0
        )
    }

    // dp[i][j] = length of common suffix of lhs[0..i-1] and rhs[0..j-1]
    // if those two chars match. Otherwise 0.
    val dp = Array(n + 1) { IntArray(m + 1) { 0 } }

    var maxLen = 0
    var endLhs = 0
    var endRhs = 0

    fun eq(i: Int, j: Int): Boolean =
        if (ignoreCase)
            lhs[i].lowercaseChar() == rhs[j].lowercaseChar()
        else
            lhs[i] == rhs[j]

    for (i in 1..n) {
        for (j in 1..m) {
            if (eq(i - 1, j - 1)) {
                dp[i][j] = dp[i - 1][j - 1] + 1
                if (dp[i][j] > maxLen) {
                    maxLen = dp[i][j]
                    endLhs = i   // end at (i-1) in lhs
                    endRhs = j   // end at (j-1) in rhs
                }
            } else {
                dp[i][j] = 0
            }
        }
    }

    // The substring in lhs is from (endLhs - maxLen) to endLhs-1.
    val startLhs = endLhs - maxLen
    val commonSub = lhs.substring(startLhs, endLhs)

    // The substring in rhs is from (endRhs - maxLen) to endRhs-1.
    // Usually the same text, but we record the start index for clarity.
    val startRhs = endRhs - maxLen

    return LongestCommonSubstringResult(
        length = maxLen,
        substring = commonSub,
        lhsIndex = startLhs,
        rhsIndex = startRhs
    )
}