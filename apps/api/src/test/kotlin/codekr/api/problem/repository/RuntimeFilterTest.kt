package codekr.api.problem.repository

import codekr.api.problem.entity.ProblemKind
import codekr.api.runtime.RuntimeDefinition
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 언어·런타임 필터가 무엇을 찾을지 (#618).
 *
 * **화면 없이 확인할 수 있어야 한다.** "제한 없음 문제를 함께 거는가" 가 이 필터의
 * 전부인데, 브라우저로만 확인하면 목록이 그럴듯하게 틀려도 알 수 없다.
 */
class RuntimeFilterTest {

    private val runtimes = listOf(
        runtime("python:3.13", ProblemKind.JUDGE_STDIO, harness = true),
        runtime("python:3.12", ProblemKind.JUDGE_STDIO, harness = true),
        runtime("java:21", ProblemKind.JUDGE_STDIO),
        runtime("aheui:1.2", ProblemKind.JUDGE_STDIO),
        runtime("sql:postgres16", ProblemKind.JUDGE_SQL),
        runtime("sql:mariadb11", ProblemKind.JUDGE_SQL),
    )

    @Test
    fun `언어만 고르면 그 언어의 런타임 전부다`() {
        // 사람은 "파이썬" 을 고르지 python:3.12 를 고르지 않는다.
        assertEquals(listOf("python:3.13", "python:3.12"), RuntimeFilter.of("python", null, runtimes)!!.runtimeIds)
    }

    @Test
    fun `런타임을 고르면 그것 하나로 좁힌다`() {
        assertEquals(listOf("python:3.12"), RuntimeFilter.of("python", "python:3.12", runtimes)!!.runtimeIds)
    }

    @Test
    fun `제한 없는 문제를 걸 유형이 함께 온다`() {
        /*
          **이것이 이 필터의 핵심이다.** 허용 목록이 비어 있으면 전부 허용이고(#419),
          목록의 대부분이 그렇다. 유형을 함께 주지 않으면 언어를 지정한 소수만 걸려
          **틀린 목록이 그럴듯하게 나온다.**
        */
        val python = RuntimeFilter.of("python", null, runtimes)!!

        assertTrue(ProblemKind.JUDGE_STDIO in python.kinds)
        // 파이썬은 하네스를 아는 런타임이라 함수형도 푼다 (#421).
        assertTrue(ProblemKind.JUDGE_FUNCTION in python.kinds)
        assertTrue(ProblemKind.JUDGE_SQL !in python.kinds, "파이썬으로 SQL 문제를 풀 수는 없다")
    }

    @Test
    fun `하네스를 모르는 언어는 함수형을 풀지 못한다`() {
        // `canSolve` 가 정하는 규칙을 그대로 쓴다 — 두 곳에서 따로 정하면 갈라진다.
        val java = RuntimeFilter.of("java", null, runtimes)!!

        assertTrue(ProblemKind.JUDGE_STDIO in java.kinds)
        assertTrue(ProblemKind.JUDGE_FUNCTION !in java.kinds)
    }

    @Test
    fun `SQL 은 같은 언어라도 제품이 갈린다`() {
        // postgres 와 mariadb 는 문법이 다르다 (#454). 그래서 버전 칸이 필요하다.
        assertEquals(
            listOf("sql:postgres16", "sql:mariadb11"),
            RuntimeFilter.of("sql", null, runtimes)!!.runtimeIds,
        )
        assertEquals(listOf("sql:mariadb11"), RuntimeFilter.of("sql", "sql:mariadb11", runtimes)!!.runtimeIds)
    }

    @Test
    fun `아무것도 안 고르면 거르지 않는다`() {
        // null 은 "필터 없음" 이다. 빈 결과와 구분되어야 한다.
        assertNull(RuntimeFilter.of(null, null, runtimes))
        assertNull(RuntimeFilter.of("", "", runtimes))
    }

    @Test
    fun `모르는 값을 고르면 빈 결과다`() {
        /*
          **거른 적 없는 것처럼 보이면 안 된다.** `?language=파이선` 처럼 오타가 나면
          전부 나오는 것이 아니라 아무것도 안 나와야 사람이 잘못을 안다.
        */
        assertTrue(RuntimeFilter.of("파이선", null, runtimes)!!.isEmpty)
    }

    @Test
    fun `런타임 id 앞부분이 언어다`() {
        assertEquals("python", RuntimeFilter.languageOf("python:3.13"))
        assertEquals("sql", RuntimeFilter.languageOf("sql:mariadb11"))
    }

    private fun runtime(id: String, kind: ProblemKind, harness: Boolean = false) = RuntimeDefinition(
        id = id,
        label = id,
        monacoLanguage = id.substringBefore(':'),
        template = "",
        problemKind = kind,
        supportsFunctionHarness = harness,
    )
}
