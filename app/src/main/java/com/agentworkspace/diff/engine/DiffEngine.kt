package com.agentworkspace.diff.engine

/**
 * Simple line-based diff engine.
 * Generates unified diff format patches.
 */
object DiffEngine {

    /**
     * Generate a unified diff between two strings.
     */
    fun generateDiff(
        original: String,
        new: String,
        filePath: String = "file",
    ): String {
        val originalLines = original.split("\n")
        val newLines = new.split("\n")

        val diffs = computeLcsDiff(originalLines, newLines)

        val sb = StringBuilder()
        sb.appendLine("--- a/$filePath")
        sb.appendLine("+++ b/$filePath")

        var oldLine = 1
        var newLine = 1

        for (diff in diffs) {
            when (diff) {
                is DiffLine.Equal -> {
                    oldLine++
                    newLine++
                }
                is DiffLine.Add -> {
                    if (sb.isEmpty() || !sb.endsWith("+${diff.content}\n")) {
                        sb.appendLine("@@ -${oldLine},1 +${newLine},1 @@")
                    }
                    sb.appendLine("+${diff.content}")
                    newLine++
                }
                is DiffLine.Remove -> {
                    sb.appendLine("@@ -${oldLine},1 +${newLine},0 @@")
                    sb.appendLine("-${diff.content}")
                    oldLine++
                }
            }
        }

        return sb.toString()
    }

    /**
     * Compute the diff using LCS (Longest Common Subsequence) algorithm.
     */
    private fun computeLcsDiff(original: List<String>, new: List<String>): List<DiffLine> {
        val m = original.size
        val n = new.size

        // Build LCS table
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0 until m) {
            for (j in 0 until n) {
                if (original[i] == new[j]) {
                    dp[i + 1][j + 1] = dp[i][j] + 1
                } else {
                    dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        // Backtrack to find diff
        val result = mutableListOf<DiffLine>()
        var i = m
        var j = n
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && original[i - 1] == new[j - 1] -> {
                    result.add(0, DiffLine.Equal(original[i - 1]))
                    i--; j--
                }
                j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                    result.add(0, DiffLine.Add(new[j - 1]))
                    j--
                }
                i > 0 -> {
                    result.add(0, DiffLine.Remove(original[i - 1]))
                    i--
                }
            }
        }

        return result
    }

    /**
     * Get a list of diff lines with their type for UI rendering.
     */
    fun getDiffLines(original: String, new: String): List<DiffLineDisplay> {
        val originalLines = original.split("\n")
        val newLines = new.split("\n")
        val diffs = computeLcsDiff(originalLines, newLines)

        return diffs.map { diff ->
            when (diff) {
                is DiffLine.Equal -> DiffLineDisplay(diff.content, DiffLineType.UNCHANGED)
                is DiffLine.Add -> DiffLineDisplay(diff.content, DiffLineType.ADDED)
                is DiffLine.Remove -> DiffLineDisplay(diff.content, DiffLineType.REMOVED)
            }
        }
    }
}

sealed class DiffLine {
    data class Equal(val content: String) : DiffLine()
    data class Add(val content: String) : DiffLine()
    data class Remove(val content: String) : DiffLine()
}

data class DiffLineDisplay(
    val content: String,
    val type: DiffLineType,
)

enum class DiffLineType {
    UNCHANGED, ADDED, REMOVED
}
