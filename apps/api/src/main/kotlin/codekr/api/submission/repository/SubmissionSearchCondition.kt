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
    /**
     * 문제를 가리키는 값. **번호와 slug 를 둘 다 받는다** (#600).
     *
     * 전에는 이름이 `problemSlug` 였고 slug 로만 풀었다. 그런데 문제 상세는 번호로도
     * 열리므로(#204) `/problems/9/submissions` 에서 `"9"` 가 그대로 넘어왔고,
     * 숫자로만 된 slug 는 만들 수 없어 **조건이 늘 빈 집합**이 됐다.
     */
    val problemKey: String? = null,
    val nickname: String? = null,
    val runtimeId: String? = null,
    val verdict: Verdict? = null,
    val submittedFrom: Instant? = null,
    val submittedTo: Instant? = null,
    val sort: SubmissionSort = SubmissionSort.LATEST,
)
