package codekr.api.security

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 어드민 API 권한이 실제로 걸려 있는지 확인한다 (#71).
 *
 * 개별 테스트를 하나씩 쌓는 대신 **매핑 전체를 훑는** 이유는, 새 컨트롤러에서 권한을
 * 빼먹는 것이 정확히 개별 테스트가 놓치는 실수이기 때문이다. 정상 흐름은 늘 권한 있는
 * 계정으로만 눌러 보므로 통과한다.
 */
class EndpointAuthorizationIntegrationTest : IntegrationTestBase() {

    // actuator 도 같은 타입의 빈을 등록한다. MVC 컨트롤러 매핑만 봐야 한다.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingHandlerMapping
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var userToken: String

    /**
     * 역할별 토큰. `@TestFactory` 의 동적 테스트는 **하나의 테스트 메서드 안에서** 돌기
     * 때문에 매번 사용자를 만들면 이메일·닉네임이 겹친다. 역할당 한 명만 만든다.
     */
    private val tokensByRole = mutableMapOf<UserRole, String>()

    @BeforeEach
    fun issueUserToken() {
        tokensByRole.clear()
        userToken = tokenFor(UserRole.USER)
    }

    private fun tokenFor(role: UserRole): String = tokensByRole.getOrPut(role) {
        tokenProvider.issueAccessToken(
            userRepository.save(
                User("${role.name.lowercase()}@codekr.dev", "x", "계정-${role.name}", setOf(role, UserRole.USER)),
            ),
        )
    }

    @Test
    fun `모든 매핑이 접근 수준 목록에 등록되어 있다`() {
        val declared = ApiEndpointInventory.ALL.map { "${it.method} ${it.pattern}" }.toSet()
        val undeclared = actualEndpoints() - declared

        assertTrue(
            undeclared.isEmpty(),
            "접근 수준이 정해지지 않은 엔드포인트가 있습니다. ApiEndpointInventory 에 추가하세요:\n" +
                undeclared.sorted().joinToString("\n"),
        )
    }

    @Test
    fun `사라진 엔드포인트가 목록에 남아 있지 않다`() {
        // 지워진 API 가 목록에 남으면 위 검사가 통과해도 목록을 믿을 수 없게 된다.
        val stale = ApiEndpointInventory.ALL.map { "${it.method} ${it.pattern}" }.toSet() - actualEndpoints()

        assertTrue(stale.isEmpty(), "사라진 엔드포인트가 목록에 남아 있습니다:\n${stale.sorted().joinToString("\n")}")
    }

    @Test
    fun `어드민 엔드포인트는 모두 admin 경로 아래에 있다`() {
        // 경로 규칙(SecurityConfig 의 /api/v1/admin/**)이 유일한 방어선이라,
        // 그 경로 밖에 어드민 API 가 생기면 아무 보호도 받지 못한다.
        val outside = ApiEndpointInventory.ADMIN_ONLY.filterNot { it.pattern.startsWith("/api/v1/admin/") }

        assertTrue(outside.isEmpty(), "admin 경로 밖의 어드민 엔드포인트: $outside")
    }

    @TestFactory
    fun `토큰이 없으면 401 이다`(): List<DynamicTest> =
        ApiEndpointInventory.PROTECTED.map { endpoint ->
            DynamicTest.dynamicTest(endpoint.toString()) {
                assertEquals(401, call(endpoint, token = null), "$endpoint 는 토큰 없이 접근할 수 없어야 합니다")
            }
        }

    @TestFactory
    fun `일반 사용자는 어드민 엔드포인트에 접근할 수 없다`(): List<DynamicTest> =
        ApiEndpointInventory.ADMIN_ONLY.map { endpoint ->
            DynamicTest.dynamicTest(endpoint.toString()) {
                assertEquals(403, call(endpoint, token = userToken), "$endpoint 는 일반 사용자에게 막혀야 합니다")
            }
        }

    /**
     * 역할이 다른 어드민은 남의 영역에 못 들어간다 (#103).
     *
     * **이게 이 이슈의 핵심이다.** 어드민이면 전부 할 수 있던 것을 나누는 것이 목적이므로,
     * "어드민이 아니면 막힌다" 가 아니라 "역할이 다르면 막힌다" 를 확인해야 한다.
     */
    @TestFactory
    fun `역할이 다르면 어드민 엔드포인트에 접근할 수 없다`(): List<DynamicTest> =
        ApiEndpointInventory.ADMIN_ONLY.mapNotNull { endpoint ->
            val other = otherAdminRole(requireNotNull(endpoint.role)) ?: return@mapNotNull null
            DynamicTest.dynamicTest("$endpoint ← ${other.name}") {
                assertEquals(403, call(endpoint, tokenFor(other)), "$endpoint 는 ${other.name} 에게 막혀야 합니다")
            }
        }

    @TestFactory
    fun `요구하는 역할을 가지면 인가를 통과한다`(): List<DynamicTest> =
        ApiEndpointInventory.ADMIN_ONLY.map { endpoint ->
            DynamicTest.dynamicTest(endpoint.toString()) {
                val role = requireNotNull(endpoint.role)
                // 자원이 없어 404/400 이 날 수는 있어도 **인가로 막히면 안 된다.**
                assertNotEquals(403, call(endpoint, tokenFor(role)), "$endpoint 는 ${role.name} 에게 열려야 합니다")
            }
        }

    @Test
    fun `최고 관리자는 모든 어드민 엔드포인트를 통과한다`() {
        val token = tokenFor(UserRole.SUPERUSER)

        // 위계가 없으면 최고 관리자에게 모든 역할을 일일이 붙여야 한다.
        val blocked = ApiEndpointInventory.ADMIN_ONLY.filter { call(it, token) == 403 }

        assertTrue(blocked.isEmpty(), "최고 관리자가 막힌 엔드포인트: $blocked")
    }

    /** 그 엔드포인트가 요구하지 않는 다른 어드민 역할 하나. 위계상 상위 역할은 제외한다. */
    private fun otherAdminRole(required: UserRole): UserRole? =
        when (required) {
            // ADMIN 이 필요한 곳에는 담당 역할(하위)로 들어갈 수 없어야 한다.
            UserRole.ADMIN -> UserRole.PROBLEM_SETTER
            // 출제자 영역에는 다른 담당 역할로 들어갈 수 없어야 한다.
            UserRole.PROBLEM_SETTER -> UserRole.BOARD_MANAGER
            else -> null
        }

    /** 실제 등록된 매핑을 "METHOD /경로" 집합으로 만든다. */
    private fun actualEndpoints(): Set<String> =
        handlerMapping.handlerMethods.keys
            .flatMap { info ->
                val patterns = info.pathPatternsCondition?.patternValues.orEmpty()
                val methods = info.methodsCondition.methods.map { it.name }
                // `/api/` 밖에도 **사람이 손으로 적어 붙이는 주소**가 있다 (#475).
                // 프로필 배지는 남의 README 에 박히므로 우리가 옮길 수 없고, 그래서
                // 버전이 붙는 `/api/v1` 아래에 두지 않았다. 인가 목록은 그것도 본다 —
                // 목록 밖에 있으면 접근 수준이 정해지지 않은 채로 열려 있게 된다.
                patterns.filter { it.startsWith("/api/") || it.startsWith("/badge/") }
                    .flatMap { pattern -> methods.map { method -> "$method $pattern" } }
            }
            .toSet()

    /**
     * 경로 변수는 아무 값이나 채운다. 여기서 보는 것은 **인가 결과**이므로 자원이 실제로
     * 있는지는 상관없다 — 인가는 자원 조회보다 먼저 일어난다.
     */
    private fun call(endpoint: Endpoint, token: String?): Int {
        val path = endpoint.pattern.replace(Regex("\\{[^}]+}"), "1")
        val builder = request(HttpMethod.valueOf(endpoint.method), path)
            .contentType("application/json")
            .content("{}")
        token?.let { builder.header("Authorization", "Bearer $it") }
        return mockMvc.perform(builder).andReturn().response.status
    }
}
