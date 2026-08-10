package codekr.api.queue

import codekr.api.problem.entity.Problem
import codekr.api.submission.entity.SubmissionKind

/**
 * 채점 우선순위 (#102).
 *
 * **이 값은 서버만 정한다.** API 요청 어디에도 우선순위 입력을 두지 않는다 —
 * 두는 순간 사용자가 자기 제출을 앞으로 당길 수 있다. 실행 제한을 클라이언트에서 받지
 * 않는 것과 같은 이유다 (docs/06_실행_제약_계약.md).
 *
 * 등급은 메시지 필드가 아니라 **스트림**으로 표현한다. 어느 스트림에 넣을지는 발행자만
 * 정하므로 조작할 여지 자체가 없다.
 */
enum class JudgePriority(val stream: String) {
    /** 어드민의 정답 코드 검증 (#39). 문제 공개를 막고 있으므로 먼저 처리한다. */
    HIGH(QueueKeys.JUDGE_STREAM_HIGH),

    /** 일반 사용자 제출. */
    NORMAL(QueueKeys.JUDGE_STREAM_NORMAL),

    /** 실행이 무거워 다른 문제를 밀어내는 문제. 어드민이 문제마다 내릴 수 있다. */
    LOW(QueueKeys.JUDGE_STREAM_LOW),
    ;

    companion object {
        /**
         * 제출 종류와 문제 설정만 보고 등급을 정한다.
         *
         * **HIGH 는 시스템 동작에만 준다.** 어드민이 문제를 HIGH 로 올릴 수 있으면
         * 결국 모든 문제가 HIGH 가 되고 등급이 의미를 잃는다. 문제 설정으로 고를 수
         * 있는 것은 NORMAL 과 LOW 뿐이다 (ProblemJudgePriority).
         */
        fun of(kind: SubmissionKind, problem: Problem): JudgePriority = when (kind) {
            SubmissionKind.SOLUTION_VERIFICATION -> HIGH
            SubmissionKind.USER -> problem.judgePriority.toQueuePriority()
        }
    }
}
