package codekr.api.problem.draft

import org.springframework.web.client.HttpStatusCodeException
import tools.jackson.databind.ObjectMapper

/**
 * 모델 제공자 오류를 **로그에 남겨도 되는 한 줄**로 바꾼다 (#663).
 *
 * 전에는 `failure.message` 를 그대로 적었다. `RestClient` 의 예외는 메시지에 **응답
 * 본문을 통째로** 붙이는데, LiteLLM 은 거기에 제공자가 만든 마스킹된 키를 실어 보낸다.
 *
 * ```
 * Incorrect API key provided: sk-proj-****…****<끝 4자>
 * Received Model Group=문제-초안  Available Model Group Fallbacks=[…]
 * "request_id":"req_…"
 * ```
 *
 * 가려져 있어도 **어느 키인지 식별하기에는 충분하고**, 모델 그룹·폴백 목록·제공자
 * request_id 까지 함께 남는다. #355 가 메일에서 세운 규칙("자격증명은 로그에 남기지
 * 않는다")이 여기 주석에도 적혀 있었는데 실제로는 지켜지지 않고 있었다.
 *
 * **`MailSender.scrub` 방식은 여기서 안 통한다.** 그쪽은 *우리가 아는* 비밀 문자열을
 * 지우는데, 여기서 새는 조각은 **제공자가 만든 것이라 우리가 가진 적이 없다.**
 * 그래서 지우는 대신 **애초에 넣지 않는다** — 본문에서 꺼내는 것은 상태 코드와
 * `error.type` 뿐이다.
 */
object ProviderFailure {

    /**
     * 본문에서 꺼낸 값이 이 모양이 아니면 버린다.
     *
     * **이것이 안전의 근거다.** `error.type` 은 제공자가 정한 짧은 식별자
     * (`authentication_error`·`rate_limit_error`)라 키가 들어갈 자리가 없다.
     * 그래도 "그럴 것이다" 에 기대지 않는다 — 모양이 다르면 그냥 안 싣는다.
     */
    private val SAFE_TYPE = Regex("^[A-Za-z0-9_.-]{1,64}$")

    /**
     * 로그 한 줄. **본문은 절대 들어가지 않는다.**
     *
     * 401·403 을 따로 말하는 이유: 운영자가 알아야 할 것은 "키가 틀렸다" 이고,
     * 그것은 "모델이 느리다" 와 **완전히 다른 조치**를 부른다. 상태 코드만 남기면
     * 로그를 보는 사람이 그 차이를 매번 다시 찾아봐야 한다.
     */
    fun describe(failure: Throwable, objectMapper: ObjectMapper): String {
        if (failure !is HttpStatusCodeException) {
            // 연결 실패·타임아웃. 본문이 없으므로 종류만 남긴다.
            return "연결 실패(${failure.javaClass.simpleName})"
        }

        val status = failure.statusCode.value()
        val type = errorType(failure, objectMapper)?.let { " type=$it" } ?: ""

        return when (status) {
            401, 403 -> "$status 인증 실패 — 키가 틀렸거나 권한이 없습니다$type"
            429 -> "$status 한도 초과 — 잠시 뒤 다시 됩니다$type"
            else -> "$status 제공자 오류$type"
        }
    }

    /** 본문에서 `error.type` **하나만** 꺼낸다. `error.message` 는 읽지 않는다. */
    private fun errorType(failure: HttpStatusCodeException, objectMapper: ObjectMapper): String? =
        runCatching {
            objectMapper.readTree(failure.responseBodyAsString)
                .path("error").path("type").asString()
        }.getOrNull()?.takeIf { SAFE_TYPE.matches(it) }
}
