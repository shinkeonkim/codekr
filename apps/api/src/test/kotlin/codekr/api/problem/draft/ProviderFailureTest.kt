package codekr.api.problem.draft

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.HttpHeaders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 제공자 오류가 나도 로그에 키 조각이 남지 않는다 (#663).
 *
 * **실제로 나왔던 본문을 그대로 쓴다.** 지어낸 본문으로 시험하면 "우리가 예상한 모양"
 * 만 막게 되는데, 이 사고는 정확히 **예상하지 못한 것이 본문에 실려서** 났다.
 */
class ProviderFailureTest {

    private val objectMapper = JsonMapper()

    /**
     * #648 을 확인하다 실제로 로그에 나온 본문. 키 부분은 여기서 다시 가렸다.
     *
     * **한 줄이다.** 처음에 보기 좋으라고 여러 줄로 쪼갰더니 JSON 이 깨졌고, 그러면
     * `type` 을 못 꺼내 안전한 쪽으로만 떨어져서 **정작 확인하려던 경로를 안 지났다.**
     */
    private val leakyBody = listOf(
        """{"error":{"message":"litellm.AuthenticationError: OpenAIException - """,
        """Incorrect API key provided: sk-proj-****ABCD. You can find your API key at """,
        """https://platform.openai.com. Received Model Group=문제-초안  """,
        """Available Model Group Fallbacks=['문제-초안-예비'] Error doing the fallback: """,
        """AnthropicException - request_id req_01xyz",""",
        """"type":"authentication_error","code":"invalid_api_key"}}""",
    ).joinToString("")

    /**
     * **`RestClient` 가 실제로 던지는 모양으로 만든다.**
     *
     * 인자가 다섯인 `create` 는 메시지가 `"401 Unauthorized"` 뿐이다. 그것으로
     * 시험하면 **새는 경로를 아예 안 지난다** — 처음에 그렇게 썼다가 "옛 방식도
     * 안전하다" 는 엉뚱한 결론이 나왔다.
     *
     * `DefaultResponseErrorHandler` 는 본문을 붙인 메시지를 만들어 **인자가 여섯인**
     * 쪽으로 던진다. 이 이슈가 본 로그 줄이 그것이다.
     */
    private fun httpError(status: HttpStatus, body: String) = HttpClientErrorException.create(
        """${status.value()} ${status.reasonPhrase}: "$body"""",
        status,
        status.reasonPhrase,
        HttpHeaders(),
        body.toByteArray(StandardCharsets.UTF_8),
        StandardCharsets.UTF_8,
    )

    @Test
    fun `키 조각도 본문도 로그 줄에 들어가지 않는다`() {
        val line = ProviderFailure.describe(httpError(HttpStatus.UNAUTHORIZED, leakyBody), objectMapper)

        // 가려진 키라도 접두사와 끝 네 자면 어느 키인지 식별된다.
        assertFalse(line.contains("sk-"), "키 조각이 남았습니다: $line")
        assertFalse(line.contains("ABCD"), "키 끝자리가 남았습니다: $line")
        // 제공자 쪽 세부도 남기지 않는다 — 모델 그룹·폴백 목록·플랫폼 주소.
        for (leak in listOf("문제-초안", "Fallbacks", "platform.openai.com", "AnthropicException")) {
            assertFalse(line.contains(leak), "본문 조각 '$leak' 가 남았습니다: $line")
        }
    }

    /**
     * 401 은 "키 문제" 라고 말한다.
     *
     * 상태 코드만 남기면 로그를 보는 사람이 그 차이를 매번 다시 찾아본다.
     * "키가 틀렸다" 와 "모델이 느리다" 는 완전히 다른 조치를 부른다.
     */
    @Test
    fun `인증 실패와 한도 초과를 구별해 말한다`() {
        val auth = ProviderFailure.describe(httpError(HttpStatus.UNAUTHORIZED, leakyBody), objectMapper)
        assertTrue(auth.contains("인증 실패"), auth)
        // 원인을 아주 잃지는 않는다 — type 은 짧은 식별자라 실을 수 있다.
        assertTrue(auth.contains("type=authentication_error"), auth)

        val rate = ProviderFailure.describe(
            httpError(HttpStatus.TOO_MANY_REQUESTS, """{"error":{"type":"rate_limit_error"}}"""),
            objectMapper,
        )
        assertTrue(rate.contains("한도 초과"), rate)
        assertFalse(rate.contains("인증"), rate)
    }

    /**
     * `type` 이 짧은 식별자 모양이 아니면 버린다.
     *
     * **"그럴 것이다" 에 기대지 않는다.** 제공자가 거기에 무엇을 넣을지는 우리가
     * 정하지 못한다 — 프록시(#648) 뒤에 여러 제공자가 있으면 더 그렇다.
     */
    @Test
    fun `type 이 이상한 모양이면 싣지 않는다`() {
        val line = ProviderFailure.describe(
            httpError(HttpStatus.BAD_REQUEST, """{"error":{"type":"key sk-proj-ABCD is wrong"}}"""),
            objectMapper,
        )

        assertFalse(line.contains("sk-"), "이상한 type 이 그대로 실렸습니다: $line")
        assertEquals("400 제공자 오류", line)
    }

    @Test
    fun `본문이 없거나 JSON 이 아니어도 죽지 않는다`() {
        assertEquals("500 제공자 오류", ProviderFailure.describe(httpError(HttpStatus.INTERNAL_SERVER_ERROR, "<html>"), objectMapper))
        assertEquals(
            "연결 실패(ResourceAccessException)",
            ProviderFailure.describe(ResourceAccessException("타임아웃", IOException("timeout")), objectMapper),
        )
    }
}
