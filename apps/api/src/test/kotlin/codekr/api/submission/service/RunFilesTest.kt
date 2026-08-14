package codekr.api.submission.service

import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.entity.ProblemSqlSpec
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 실행에 함께 실을 파일 (#525).
 *
 * **여기서 지키는 것은 두 가지다** — SQL 실행이 스키마 없이 돌지 않을 것, 그리고
 * **정답이 실행 응답으로 새어 나가지 않을 것.**
 */
class RunFilesTest {

    private fun spec(allowWrite: Boolean = false) = ProblemSqlSpec(
        problemId = 1,
        schemaSql = "CREATE TABLE members (id int);",
        answerSql = "SELECT id FROM members;",
        ignoreRowOrder = true,
        verifySql = "SELECT count(*) FROM members;",
        allowWrite = allowWrite,
    )

    @Test
    fun `SQL 실행에는 스키마가 함께 간다`() {
        // 없으면 어떤 쿼리를 써도 relation does not exist 다 — 실행이 아무 쓸모가 없다.
        val files = RunFiles.of(ProblemKind.JUDGE_SQL, spec())

        assertEquals("CREATE TABLE members (id int);", files["schema.sql"])
    }

    @Test
    fun `정답과 검사 쿼리는 실리지 않는다`() {
        /*
          **실행 결과는 그대로 사용자에게 돌아간다.** 정답이 실렸다면 하네스가
          `--- codekr:expected` 로 기대 결과를 찍고, 그것이 화면에 그대로 보인다.
        */
        val files = RunFiles.of(ProblemKind.JUDGE_SQL, spec())

        assertFalse(files.containsKey("answer.sql"), "정답이 실렸다: $files")
        assertFalse(files.containsKey("verify.sql"), "검사 쿼리가 실렸다: $files")
        assertFalse(files.values.any { it.contains("SELECT id FROM members") }, "정답 내용이 샜다: $files")
    }

    @Test
    fun `쓰기를 여는 문제는 실행에서도 열린다`() {
        // 아니면 실행에서만 막혀서, 되는 쿼리를 두고 사용자가 자기 쿼리를 의심한다 (#453).
        assertTrue(RunFiles.of(ProblemKind.JUDGE_SQL, spec(allowWrite = true)).containsKey("allow-write"))
        assertFalse(RunFiles.of(ProblemKind.JUDGE_SQL, spec(allowWrite = false)).containsKey("allow-write"))
    }

    @Test
    fun `SQL 이 아닌 유형은 아무것도 싣지 않는다`() {
        assertEquals(emptyMap(), RunFiles.of(ProblemKind.JUDGE_STDIO, spec()))
    }

    @Test
    fun `스펙이 없으면 아무것도 싣지 않는다`() {
        // 스펙 없는 SQL 문제는 만들 수 없지만(#60 의 검증), 없다고 터지지는 않아야 한다.
        assertEquals(emptyMap(), RunFiles.of(ProblemKind.JUDGE_SQL, null))
    }
}
