package codekr.api.submission.repository

import codekr.api.submission.entity.Verdict
import java.time.Instant

enum class SubmissionSort { LATEST, OLDEST, RUNTIME, MEMORY }

/**
 * 전체 제출 목록의 검색 조건 (#34).
 *
 * 값이 없는 필드는 조건에서 빠진다. 조합해도 결과와 전체 건수가 일치해야 하므로
 * 조건 조립과 카운트가 같은 술어를 쓴다.
 */
data class SubmissionSearchCondition(
    val problemSlug: String? = null,
    val nickname: String? = null,
    val runtimeId: String? = null,
    val verdict: Verdict? = null,
    val submittedFrom: Instant? = null,
    val submittedTo: Instant? = null,
    val sort: SubmissionSort = SubmissionSort.LATEST,
)
