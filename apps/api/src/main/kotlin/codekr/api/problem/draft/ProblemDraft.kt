package codekr.api.problem.draft

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.ProblemCategory
import org.springframework.boot.context.properties.ConfigurationProperties
import tools.jackson.databind.JsonNode

/**
 * 지문에서 뽑아낸 초안 (#230).
 *
 * **폼을 통째로 대신하지 않는다.** 여기 없는 것들 — 정답 코드, 언어별 제한(#97),
 * 비공개 테스트케이스 — 은 사람이 채운다. 지문에 있는 것만 뽑는 것이 이 도구의 범위다.
 */
data class ProblemDraftResponse(
    val title: String,
    val inputDescription: String,
    val outputDescription: String,
    val examples: List<DraftExample>,
    /** 제안일 뿐이다. 화면이 "AI 가 제안함" 으로 보여 준다. */
    val category: String?,
    val difficulty: String?,
    val timeLimitMs: Int?,
    val memoryLimitMb: Int?,
    /**
     * 모델이 판단하지 못한 것.
     *
     * **빈 값을 지어내는 것보다 못 찾았다고 말하는 편이 낫다** — 지어낸 값은 사람이
     * 검토하면서 그럴듯해 보여 그대로 지나간다.
     */
    val missing: List<String>,
)

data class DraftExample(val input: String, val output: String)

/**
 * 초안 도구 설정 (#230).
 *
 * **키가 없으면 기능이 없다.** 저장소(#115)·메일(#233)과 같은 규칙이다 — 없는 채로
 * 뜨고, 부르면 404 다. 켜야 쓸 수 있는 것과 꺼져 있어도 서비스가 도는 것은 다른 문제다.
 */
@ConfigurationProperties(prefix = "codekr.problem-draft")
data class ProblemDraftProperties(
    /** OpenAI 호환 엔드포인트. 제공자를 바꿔 끼울 수 있게 주소로 받는다. */
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    /** 붙여 넣을 수 있는 지문 길이 상한. 길수록 비용이고, 지문은 대개 짧다. */
    val maxStatementLength: Int = 8_000,
    /** 한 사람이 이 분에 부를 수 있는 횟수. 어드민 기능이라 낮게 잡아도 불편하지 않다. */
    val perMinuteLimit: Int = 10,
) {
    val enabled: Boolean get() = apiKey.isNotBlank()
}

/**
 * 모델이 돌려준 것을 **믿지 않고** 초안으로 만든다 (#230).
 *
 * 모델의 출력은 사용자 입력과 같은 급으로 다룬다 — 길이도, 값의 범위도, 개수도
 * 여기서 자른다. 그대로 폼에 흘려보내면 잘못된 값이 사람 눈을 거쳐 저장된다.
 */
object DraftParser {

    private const val MAX_TITLE = 100
    private const val MAX_TEXT = 2_000
    private const val MAX_EXAMPLES = 5
    private const val MAX_EXAMPLE_TEXT = 1_000

    fun parse(node: JsonNode): ProblemDraftResponse {
        val title = text(node, "title", MAX_TITLE)
        if (title.isBlank()) {
            // 제목조차 못 뽑았다면 지문이 아니거나 모델이 엉뚱한 것을 돌려준 것이다.
            throw ApiException(ErrorCode.VALIDATION_ERROR, "지문에서 초안을 만들지 못했습니다.")
        }

        return ProblemDraftResponse(
            title = title,
            inputDescription = text(node, "inputDescription", MAX_TEXT),
            outputDescription = text(node, "outputDescription", MAX_TEXT),
            examples = examples(node),
            category = enumOrNull(node, "category", CATEGORIES),
            difficulty = enumOrNull(node, "difficulty", DIFFICULTIES),
            timeLimitMs = intOrNull(node, "timeLimitMs", 100, 60_000),
            memoryLimitMb = intOrNull(node, "memoryLimitMb", 16, 2_048),
            missing = missing(node),
        )
    }

    private fun text(node: JsonNode, field: String, limit: Int): String =
        node.path(field).takeIf { it.isString }?.stringValue()?.trim()?.take(limit) ?: ""

    /**
     * 예제 입출력.
     *
     * **입력이나 출력 한쪽만 있는 것은 버린다.** 반쪽짜리 예제는 테스트케이스로 쓸 수
     * 없고, 폼에 들어가면 사람이 그것을 지우는 일부터 해야 한다.
     */
    private fun examples(node: JsonNode): List<DraftExample> {
        val array = node.path("examples")
        if (!array.isArray) return emptyList()
        return array.mapNotNull { each ->
            val input = each.path("input").takeIf { it.isString }?.stringValue()?.take(MAX_EXAMPLE_TEXT)
            val output = each.path("output").takeIf { it.isString }?.stringValue()?.take(MAX_EXAMPLE_TEXT)
            if (input.isNullOrBlank() || output.isNullOrBlank()) null else DraftExample(input, output)
        }.take(MAX_EXAMPLES)
    }

    /** 모르는 값은 **null 이다.** 없는 enum 이름을 그대로 흘리면 폼이 그것을 고르지 못한다. */
    private fun enumOrNull(node: JsonNode, field: String, allowed: Set<String>): String? =
        node.path(field).takeIf { it.isString }?.stringValue()?.uppercase()?.takeIf { it in allowed }

    private fun intOrNull(node: JsonNode, field: String, min: Int, max: Int): Int? =
        node.path(field).takeIf { it.isNumber }?.asInt()?.takeIf { it in min..max }

    private fun missing(node: JsonNode): List<String> {
        val array = node.path("missing")
        if (!array.isArray) return emptyList()
        return array.mapNotNull { it.takeIf { each -> each.isString }?.stringValue()?.take(50) }.take(10)
    }

    /**
     * 폼이 고를 수 있는 값만 받는다.
     *
     * **enum 에서 직접 가져온다.** 손으로 베껴 적으면 유형이나 티어가 하나 늘 때
     * 조용히 갈라지고, 그때 모델이 맞게 뽑아도 여기서 버려진다.
     * 프롬프트에 적는 목록도 같은 곳에서 나온다.
     */
    val CATEGORIES: Set<String> = ProblemCategory.entries.map { it.name }.toSet()
    val DIFFICULTIES: Set<String> = DifficultyTier.entries.map { it.name }.toSet()
}
