package codekr.api.problem

import codekr.api.common.error.ApiException
import codekr.api.problem.draft.DraftParser
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.ProblemCategory
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 모델이 돌려준 것을 믿지 않는다 (#230).
 *
 * **모델의 출력은 사용자 입력과 같은 급이다.** 그대로 폼에 흘려보내면 잘못된 값이
 * 사람 눈을 거쳐 저장된다 — 검토는 그럴듯한 것을 통과시킨다.
 */
class DraftParserTest {

    private val mapper = JsonMapper.builder().build()

    private fun parse(json: String) = DraftParser.parse(mapper.readTree(json))

    @Test
    fun `지문에서 뽑은 것을 그대로 옮긴다`() {
        val draft = parse(
            """
            {
              "title": "두 수의 합",
              "inputDescription": "첫째 줄에 A와 B",
              "outputDescription": "A + B",
              "examples": [{"input": "1 2", "output": "3"}],
              "category": "ALGORITHM",
              "difficulty": "BRONZE",
              "timeLimitMs": 2000,
              "memoryLimitMb": 256,
              "missing": []
            }
            """,
        )

        assertEquals("두 수의 합", draft.title)
        assertEquals(1, draft.examples.size)
        assertEquals("ALGORITHM", draft.category)
        assertEquals(2000, draft.timeLimitMs)
    }

    @Test
    fun `없는 유형은 버린다`() {
        // 폼이 고를 수 없는 값을 흘리면 화면이 그것을 표시하지도, 저장하지도 못한다.
        val draft = parse("""{"title": "t", "category": "COMPETITIVE", "difficulty": "MASTER"}""")

        assertNull(draft.category)
        assertNull(draft.difficulty)
    }

    @Test
    fun `고를 수 있는 값 목록은 코드에서 온다`() {
        /*
          손으로 베껴 적으면 유형이 하나 늘 때 조용히 갈라지고, 그때 모델이 맞게
          뽑아도 여기서 버려진다. **이 시험이 그 갈라짐을 잡는다.**
        */
        assertEquals(ProblemCategory.entries.map { it.name }.toSet(), DraftParser.CATEGORIES)
        assertEquals(DifficultyTier.entries.map { it.name }.toSet(), DraftParser.DIFFICULTIES)
    }

    @Test
    fun `반쪽짜리 예제는 버린다`() {
        // 입력만 있는 예제는 테스트케이스가 될 수 없고, 폼에 들어가면 지우는 일부터 해야 한다.
        val draft = parse(
            """{"title": "t", "examples": [{"input": "1"}, {"input": "1", "output": " "}, {"input": "2", "output": "3"}]}""",
        )

        assertEquals(1, draft.examples.size)
        assertEquals("2", draft.examples[0].input)
    }

    @Test
    fun `예제가 아무리 많아도 다섯까지만 받는다`() {
        val many = (1..50).joinToString(",") { """{"input":"$it","output":"$it"}""" }
        val draft = parse("""{"title": "t", "examples": [$many]}""")

        assertEquals(5, draft.examples.size)
    }

    @Test
    fun `말도 안 되는 제한값은 버린다`() {
        // 100시간짜리 시간 제한이 폼에 들어가면 사람이 그것을 알아채지 못할 수 있다.
        val draft = parse("""{"title": "t", "timeLimitMs": 360000000, "memoryLimitMb": 999999}""")

        assertNull(draft.timeLimitMs)
        assertNull(draft.memoryLimitMb)
    }

    @Test
    fun `아주 긴 값은 잘라 낸다`() {
        val draft = parse("""{"title": "${"가".repeat(500)}", "inputDescription": "${"나".repeat(9000)}"}""")

        assertEquals(100, draft.title.length)
        assertEquals(2_000, draft.inputDescription.length)
    }

    @Test
    fun `제목이 없으면 초안이 아니다`() {
        // 지문이 아니거나 모델이 엉뚱한 것을 돌려준 것이다. 빈 폼을 채우는 척하지 않는다.
        assertFailsWith<ApiException> { parse("""{"examples": [{"input": "1", "output": "2"}]}""") }
    }

    @Test
    fun `엉뚱한 타입이 와도 터지지 않는다`() {
        /*
          `examples` 가 배열이 아니라 문자열로 오는 일이 실제로 있다.
          **터지면 사람은 손으로 채우는 길까지 잃는다** — 빈 값으로 넘긴다.
        */
        val draft = parse("""{"title": "t", "examples": "1 2 → 3", "missing": "전부", "timeLimitMs": "빠르게"}""")

        assertTrue(draft.examples.isEmpty())
        assertTrue(draft.missing.isEmpty())
        assertNull(draft.timeLimitMs)
    }

    @Test
    fun `못 찾은 것을 그대로 전한다`() {
        // 지어낸 값보다 "못 찾았다" 가 낫다. 지어낸 값은 검토를 그냥 통과한다.
        val draft = parse("""{"title": "t", "missing": ["시간 제한", "예제"]}""")

        assertEquals(listOf("시간 제한", "예제"), draft.missing)
    }
}
