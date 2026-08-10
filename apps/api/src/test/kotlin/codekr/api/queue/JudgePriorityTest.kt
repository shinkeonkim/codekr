package codekr.api.queue

import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.entity.ProblemJudgePriority
import codekr.api.submission.entity.SubmissionKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** 채점 우선순위 결정 규칙 (#102). */
class JudgePriorityTest {

    private fun problem(priority: ProblemJudgePriority = ProblemJudgePriority.NORMAL) = Problem(
        slug = "sum",
        title = "합",
        category = ProblemCategory.ALGORITHM,
        difficultyLevel = 1,
        description = "설명",
        judgePriority = priority,
    )

    @Test
    fun `어드민 정답 검증은 최상위다`() {
        // 문제 공개를 막고 있는 단계라, 사용자 제출 뒤에 서면 문제 등록이 통째로 막힌다.
        assertEquals(
            JudgePriority.HIGH,
            JudgePriority.of(SubmissionKind.SOLUTION_VERIFICATION, problem()),
        )
    }

    @Test
    fun `일반 제출은 기본 등급이다`() {
        assertEquals(JudgePriority.NORMAL, JudgePriority.of(SubmissionKind.USER, problem()))
    }

    @Test
    fun `문제를 낮은 등급으로 설정하면 사용자 제출이 뒤로 밀린다`() {
        assertEquals(
            JudgePriority.LOW,
            JudgePriority.of(SubmissionKind.USER, problem(ProblemJudgePriority.LOW)),
        )
    }

    @Test
    fun `문제 설정으로는 최상위를 고를 수 없다`() {
        // ProblemJudgePriority 에 HIGH 자체가 없어야 한다. 어드민이 문제를 최상위로
        // 올릴 수 있으면 결국 모든 문제가 그리 되고 등급이 의미를 잃는다.
        val selectable = ProblemJudgePriority.entries.map { it.toQueuePriority() }

        assertEquals(listOf(JudgePriority.NORMAL, JudgePriority.LOW), selectable)
    }

    @Test
    fun `문제 설정은 정답 검증의 등급을 바꾸지 못한다`() {
        // 문제를 LOW 로 내려도 그 문제의 정답 검증은 여전히 최상위다.
        assertEquals(
            JudgePriority.HIGH,
            JudgePriority.of(SubmissionKind.SOLUTION_VERIFICATION, problem(ProblemJudgePriority.LOW)),
        )
    }

    @Test
    fun `등급마다 스트림이 다르다`() {
        val streams = JudgePriority.entries.map { it.stream }

        assertEquals(streams.size, streams.toSet().size, "등급이 같은 스트림을 쓰면 순서가 무의미해집니다")
    }
}
