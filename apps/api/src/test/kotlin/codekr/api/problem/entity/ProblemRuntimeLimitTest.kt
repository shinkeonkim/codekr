package codekr.api.problem.entity

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 런타임별 실행 제한 오버라이드 (#97). */
class ProblemRuntimeLimitTest {

    private fun problem() = Problem(
        slug = "sum",
        title = "합",
        category = ProblemCategory.ALGORITHM,
        difficultyLevel = 1,
        description = "설명",
        timeLimitMs = 2_000,
        memoryLimitMb = 256,
    )

    @Test
    fun `오버라이드가 없으면 문제 기본 제한을 쓴다`() {
        val limits = problem().limitsFor("python:3.13")

        assertEquals(2_000, limits.timeLimitMs)
        assertEquals(256, limits.memoryLimitMb)
        assertFalse(limits.overridden)
    }

    @Test
    fun `해당 런타임만 오버라이드가 적용된다`() {
        val problem = problem().apply {
            addRuntimeLimits(listOf(ProblemRuntimeLimit("python:3.13", 6_000, 512)))
        }

        val python = problem.limitsFor("python:3.13")
        assertEquals(6_000, python.timeLimitMs)
        assertEquals(512, python.memoryLimitMb)
        assertTrue(python.overridden)

        // 적지 않은 런타임은 그대로다 — 이 표는 예외만 담는다.
        val cpp = problem.limitsFor("cpp:13")
        assertEquals(2_000, cpp.timeLimitMs)
        assertFalse(cpp.overridden)
    }

    @Test
    fun `소프트 삭제된 오버라이드는 적용되지 않는다`() {
        val problem = problem().apply {
            addRuntimeLimits(listOf(ProblemRuntimeLimit("python:3.13", 6_000, 512)))
            softDeleteRuntimeLimits()
        }

        assertFalse(problem.limitsFor("python:3.13").overridden)
    }

    @Test
    fun `런타임별 제한이 바뀌면 검증 지문도 바뀐다`() {
        // 제한은 판정을 바꾼다. 지문에 없으면 제한만 고쳤을 때 검증이 낡지 않은 것으로 남는다.
        val before = problem().verificationSignature()
        val after = problem().apply {
            addRuntimeLimits(listOf(ProblemRuntimeLimit("python:3.13", 6_000, 512)))
        }.verificationSignature()

        assertNotEquals(before, after)
    }
}
