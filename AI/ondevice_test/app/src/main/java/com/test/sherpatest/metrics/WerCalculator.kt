package com.test.sherpatest.metrics

object WerCalculator {

    /**
     * 레벤슈타인 거리 기반 WER 계산
     * WER = (삽입 + 삭제 + 대체) / 정답 단어 수
     */
    fun calculate(reference: String, hypothesis: String): Float {
        val normalize = { s: String ->
            s.trim()
             .replace(Regex("[.,!?。、··]"), "")
             .replace("\\s+".toRegex(), " ")
             .trim()
        }

        val refWords = normalize(reference).split(" ").filter { it.isNotEmpty() }
        val hypWords = normalize(hypothesis).split(" ").filter { it.isNotEmpty() }

        if (refWords.isEmpty()) return if (hypWords.isEmpty()) 0f else 1f

        val n = refWords.size
        val m = hypWords.size

        // DP 테이블 (레벤슈타인 거리)
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                dp[i][j] = if (refWords[i - 1] == hypWords[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }

        return dp[n][m].toFloat() / n.toFloat()
    }
}
