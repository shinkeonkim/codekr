package codekr.api.problem.admin.dto

/**
 * 묶음을 올린 결과 (#623).
 *
 * **언제나 목록이다.** 한 개짜리 묶음도 하나가 담긴 목록으로 온다 — 부르는 쪽이
 * 개수에 따라 다른 모양을 다루지 않게 한다.
 */
data class ProblemImportResult(val created: List<ProblemCreatedResponse>)
