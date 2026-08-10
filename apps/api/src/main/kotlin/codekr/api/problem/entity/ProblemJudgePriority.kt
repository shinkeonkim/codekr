package codekr.api.problem.entity

import codekr.api.queue.JudgePriority

/**
 * 문제마다 정할 수 있는 채점 우선순위 (#102).
 *
 * **HIGH 가 없다.** 어드민이 문제를 최상위로 올릴 수 있으면 결국 모든 문제가 그리 되고
 * 등급이 의미를 잃는다. 최상위는 시스템 동작(정답 검증)에만 남긴다.
 */
enum class ProblemJudgePriority {
    /** 기본. */
    NORMAL,

    /**
     * 뒤로 미룬다.
     *
     * 실행이 무거워 큐를 오래 잡는 문제에 쓴다 — 그 문제 하나 때문에 다른 모든 문제의
     * 채점이 밀리는 상황을 막는다.
     */
    LOW,
    ;

    fun toQueuePriority(): JudgePriority = when (this) {
        NORMAL -> JudgePriority.NORMAL
        LOW -> JudgePriority.LOW
    }
}
