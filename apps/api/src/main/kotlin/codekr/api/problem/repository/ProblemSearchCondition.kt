package codekr.api.problem.repository

import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.ProblemCategory

enum class ProblemSort { LATEST, TITLE, DIFFICULTY }

data class ProblemSearchCondition(
    val keyword: String? = null,
    val category: ProblemCategory? = null,
    val tier: DifficultyTier? = null,
    val sort: ProblemSort = ProblemSort.LATEST,
    /** null 이면 공개 여부와 무관하게 조회한다 (어드민 목록). */
    val published: Boolean? = true,
)
