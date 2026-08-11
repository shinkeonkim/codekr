package codekr.api.submission.entity

/**
 * 재채점으로 판정이 어떻게 바뀌었는지 (#187).
 *
 * **바뀌지 않은 것도 값이다.** "다시 채점했지만 결과는 그대로"라는 사실을 알 수 없으면,
 * 재채점 대상이었던 사람은 자기 결과가 확정된 것인지 아직 도는 중인지 구분할 수 없다.
 */
data class RejudgeTransition(val previous: Verdict?, val current: Verdict?) {

    val changed: Boolean get() = previous != current

    /** "틀렸습니다 → 맞았습니다" 처럼 사람이 읽는 형태. */
    fun describe(): String = "${label(previous)} → ${label(current)}"

    private fun label(verdict: Verdict?): String = verdict?.label ?: "채점 전"
}
