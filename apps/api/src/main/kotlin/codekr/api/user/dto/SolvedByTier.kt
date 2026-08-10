package codekr.api.user.dto

import codekr.api.problem.entity.DifficultyTier

/** 티어별로 몇 문제를 풀었는지. 30단계를 그대로 보여주면 한눈에 안 들어온다. */
data class SolvedByTier(val tier: DifficultyTier, val solvedCount: Int)
