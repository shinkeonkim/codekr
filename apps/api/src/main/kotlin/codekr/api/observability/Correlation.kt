package codekr.api.observability

import org.slf4j.MDC

/**
 * 제출 하나를 세 앱의 로그에서 이어 본다 (#681).
 *
 * 한 번 제출하면 로그가 최소 세 곳에 남는다(api → judge → executor). 셋이 공통으로
 * 들고 다니는 값이 없어서, "이 제출이 왜 3분 걸렸나" 를 보려면 시각으로 어림잡아 세
 * 벌을 눈으로 맞춘다 — 동시에 열 건이 돌면 그것도 못 한다.
 *
 * **MDC 를 쓰는 이유**: `logstash` 구조화 형식(#679)이 MDC 항목을 **최상위 필드로**
 * 올린다. 메시지 문자열에 `submissionId={}` 로 적으면 통짜 텍스트라 Loki 에서 못 고른다.
 *
 * > 이것이 곧 **무엇이 검색 가능한 필드가 되는가**다. 여기 넣는 값은 로그에 그대로
 * > 남는다고 보아야 한다 (#663 이 그 자리다). 제출 번호는 이미 URL 에 드러나는 값이다.
 */
object Correlation {

    /** Go 쪽 `contract.LogKeySubmission` 과 **같은 이름이어야 한다.** 다르면 쿼리가 두 벌이 된다. */
    const val SUBMISSION = "submissionId"

    /**
     * [block] 이 도는 동안의 모든 로그에 제출 번호를 붙인다.
     *
     * **앞 값을 되돌려 놓는다.** 지금은 겹쳐 부르는 곳이 없지만, 그냥 지우면 나중에
     * 겹쳤을 때 바깥 것이 조용히 사라진다 — 그런 것은 사고가 난 뒤에야 보인다.
     */
    fun <T> withSubmission(submissionId: Long, block: () -> T): T {
        val previous = MDC.get(SUBMISSION)
        MDC.put(SUBMISSION, submissionId.toString())
        try {
            return block()
        } finally {
            if (previous == null) MDC.remove(SUBMISSION) else MDC.put(SUBMISSION, previous)
        }
    }
}
