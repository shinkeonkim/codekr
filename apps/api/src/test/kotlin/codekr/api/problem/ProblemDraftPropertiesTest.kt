package codekr.api.problem

import codekr.api.problem.draft.ProblemDraftProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 초안 도구의 스위치 (#647).
 *
 * **스위치는 키 하나여야 한다.** 차트에 별도의 켜기 값을 두면 스위치가 둘이 되고,
 * 그러면 **"차트에서는 껐는데 켜져 있다"** 와 그 반대가 둘 다 가능해진다.
 * 이 기능은 실제로 뒤엣것이었다 — 코드는 들어갔는데 차트에 설정이 없어 운영에서
 * 내내 꺼져 있었고, 아무 오류도 나지 않아 아무도 몰랐다.
 *
 * 저장소(#115)·메일(#355)이 쓰는 규칙과 같다: **없으면 그 기능이 없는 것이고,
 * 서비스는 그대로 돈다.**
 */
class ProblemDraftPropertiesTest {

    @Test
    fun `키가 있으면 켜진다`() {
        assertTrue(ProblemDraftProperties(apiKey = "sk-무언가").enabled)
    }

    @Test
    fun `키가 없거나 공백뿐이면 없는 기능이다`() {
        assertFalse(ProblemDraftProperties(apiKey = "").enabled)
        // 시크릿을 지우는 대신 빈 값을 넣는 일이 흔하다. 공백도 없는 것으로 본다.
        assertFalse(ProblemDraftProperties(apiKey = "   ").enabled)
    }

    /**
     * **제공자 교체 지점은 주소다.** LiteLLM 프록시(#648)를 세우면 이 값만 바뀌고
     * api 는 고치지 않는다 — 그 성질이 기본값에 묻히지 않게 못박아 둔다.
     */
    @Test
    fun `기본값은 OpenAI 를 가리키되 주소로 받는다`() {
        assertEquals("https://api.openai.com/v1", ProblemDraftProperties().baseUrl)
        assertEquals("http://litellm:4000/v1", ProblemDraftProperties(baseUrl = "http://litellm:4000/v1").baseUrl)
    }
}
