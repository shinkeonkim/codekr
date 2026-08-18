package codekr.api.runtime

import codekr.api.runtime.controller.RuntimeLanguageResponse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 목록 필터가 고를 언어 갈래 (#618). */
class RuntimeLanguageResponseTest {

    @Test
    fun `버전이 여럿인 언어는 한 갈래로 묶인다`() {
        val languages = RuntimeLanguageResponse.from(
            listOf(runtime("python:3.13", "Python 3.13"), runtime("python:3.12", "Python 3.12")),
        )

        assertEquals(1, languages.size)
        assertEquals("Python", languages[0].label)
        assertEquals(listOf("python:3.13", "python:3.12"), languages[0].runtimes.map { it.id })
    }

    @Test
    fun `제품이 다르면 버전 칸으로 갈린다`() {
        // 같은 SQL 이지만 문법이 다르다 (#454). 갈래는 하나, 고를 것은 둘이다.
        val sql = RuntimeLanguageResponse.from(
            listOf(runtime("sql:postgres16", "PostgreSQL 16"), runtime("sql:mariadb11", "MariaDB 11")),
        ).single()

        assertEquals("SQL", sql.label)
        assertEquals(listOf("PostgreSQL 16", "MariaDB 11"), sql.runtimes.map { it.label })
    }

    @Test
    fun `이름을 안 적은 언어는 id 를 그대로 쓴다`() {
        // 새 언어가 들어와도 **화면이 비지 않는다.** 어색하면 그때 이름을 적는다.
        assertEquals("brainfuck", RuntimeLanguageResponse.from(listOf(runtime("brainfuck:1", "BF"))).single().label)
    }

    @Test
    fun `지금 등록된 언어에는 모두 이름이 있다`() {
        // 이름을 빠뜨리면 화면에 `csharp` 같은 값이 그대로 나온다.
        val ids = listOf(
            "python", "javascript", "cpp", "c", "java", "kotlin", "go", "rust",
            "ruby", "csharp", "bash", "sql", "redis", "mongodb", "aheui", "umjunsik", "interactive",
        )
        val missing = RuntimeLanguageResponse.from(ids.map { runtime("$it:1", it) })
            .filter { it.label == it.id }

        assertTrue(missing.isEmpty(), "이름을 안 적은 언어가 있습니다: ${missing.map { it.id }}")
    }

    private fun runtime(id: String, label: String) = RuntimeDefinition(
        id = id,
        label = label,
        monacoLanguage = id.substringBefore(':'),
        template = "",
    )
}
