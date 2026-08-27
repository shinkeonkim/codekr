package codekr.api.security

import codekr.api.support.IntegrationTestBase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * actuator 가 무엇을 열고 무엇을 닫는지 고정한다 (#676).
 *
 * **이 시험이 없으면 두 방향으로 조용히 틀어진다.** `/actuator/prometheus` 가 다시
 * 막히면 Prometheus 는 401 만 쌓고 아무도 모른다 — 지표는 계속 만들어지고 있으니
 * 애플리케이션 로그에도 아무 일이 없다. 반대로 `exposure.include` 에 한 줄 더 붙으면
 * 설정값이 그대로 열린다.
 *
 * 접근 수준을 컨트롤러에서 끌어내는 [EndpointAuthorizationIntegrationTest] 와 나눠 둔
 * 이유: actuator 는 **우리가 만든 컨트롤러가 아니라** 스캐너가 잡지 못한다.
 */
class ActuatorExposureIntegrationTest : IntegrationTestBase() {

    /**
     * Prometheus 는 토큰을 들고 다니지 않는다. 여기가 401 이면 `up=0` 만 쌓인다.
     *
     * 본문까지 보는 이유: 200 이라도 내용이 비면 긁을 것이 없다. `# HELP` 는
     * 텍스트 노출 형식이 실제로 붙었다는 뜻이다 (micrometer-registry-prometheus).
     */
    @Test
    fun `prometheus 는 토큰 없이 지표를 돌려준다`() {
        val response = mockMvc.perform(get("/actuator/prometheus")).andReturn().response

        assertEquals(200, response.status, "Prometheus 가 토큰 없이 읽을 수 있어야 합니다")
        assertTrue(
            response.contentAsString.contains("# HELP "),
            "지표 본문이 비어 있습니다. 실제로 받은 앞부분: ${response.contentAsString.take(200)}",
        )
    }

    @Test
    fun `health 는 그대로 열려 있다`() {
        // 쿠버네티스 프로브가 이것을 부른다. 막히면 파드가 뜨지 못한다.
        assertEquals(200, mockMvc.perform(get("/actuator/health/readiness")).andReturn().response.status)
    }

    /**
     * 설정값을 그대로 보여 주는 것들. **열려 있지 않다는 것을 두 겹으로 본다** —
     * `exposure.include` 에 없어서 404 이고, 있더라도 인증에 걸려 401 이다.
     * 둘 중 아무것도 아니면(200) 실패한다.
     */
    @TestFactory
    fun `설정을 드러내는 엔드포인트는 닫혀 있다`(): List<DynamicTest> =
        listOf("env", "configprops", "beans", "heapdump", "threaddump", "loggers", "mappings").map { name ->
            DynamicTest.dynamicTest("/actuator/$name") {
                val status = mockMvc.perform(get("/actuator/$name")).andReturn().response.status

                assertTrue(
                    status == 404 || status == 401,
                    "/actuator/$name 이 $status 로 답합니다. 노출 목록이나 인가 규칙이 열렸습니다",
                )
            }
        }

    /**
     * `metrics` 는 노출은 하지만 인증을 요구한다.
     *
     * Prometheus 가 읽는 것은 `prometheus` 하나뿐이라 이쪽까지 열 이유가 없다.
     * 열어 두면 클러스터 안의 아무 파드나 지표를 읽는다.
     */
    @Test
    fun `metrics 는 토큰을 요구한다`() {
        assertEquals(401, mockMvc.perform(get("/actuator/metrics")).andReturn().response.status)
    }
}
