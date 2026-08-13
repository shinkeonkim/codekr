package codekr.api.problem

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path

/**
 * 시드 문제 파일이 지금의 어드민 규격과 맞는가 (#420).
 *
 * **시드는 손으로 넣어 보기 전까지 아무도 확인하지 않는다.** 필드 이름이 하나 바뀌면
 * 배포한 날 운영에 문제를 못 넣는데, 그때는 이미 늦다. 여기서는 **넣어 보는 것**까지만
 * 한다 — 실제로 채점되는지는 실행기가 필요하므로 그 확인은 이 시험의 몫이 아니다.
 */
class SeedProblemIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private var token: String = ""

    @BeforeEach
    fun setUp() {
        val admin = userRepository.save(
            User("setter@codekr.dev", "x", "출제자", setOf(UserRole.USER, UserRole.PROBLEM_SETTER)),
        )
        token = tokenProvider.issueAccessToken(admin)
    }

    @Test
    fun `난해한 언어 시드는 그 언어로만 풀 수 있다`() {
        /*
          **이것이 이 문제들의 존재 이유다** (#420). 아희 문제를 파이썬으로 풀 수 있으면
          그 문제는 있을 이유가 없다. #419 의 허용 목록이 그것을 표현한다.
        */
        listOf(
            "13-aheui-sum.json" to "aheui:1.2",
            "14-umjunsik-echo.json" to "umjunsik:1.0",
        ).forEach { (file, runtimeId) ->
            create(file)
        }

        mockMvc.perform(get("/api/v1/problems/aheui-sum"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runtimeRestricted").value(true))
            .andExpect(jsonPath("$.runtimes.length()").value(1))
            .andExpect(jsonPath("$.runtimes[0].id").value("aheui:1.2"))

        mockMvc.perform(get("/api/v1/problems/umjunsik-echo"))
            .andExpect(jsonPath("$.runtimes.length()").value(1))
            .andExpect(jsonPath("$.runtimes[0].id").value("umjunsik:1.0"))
    }

    @Test
    fun `다른 언어로는 제출할 수 없다`() {
        create("13-aheui-sum.json")
        val solver = userRepository.save(User("s@codekr.dev", "x", "푸는사람", setOf(UserRole.USER)))

        mockMvc.perform(
            post("/api/v1/problems/aheui-sum/submissions")
                .header("Authorization", "Bearer ${tokenProvider.issueAccessToken(solver)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.13","sourceCode":"print(3)","visibility":"PRIVATE"}"""),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `시드에 담긴 시작 코드는 그 언어의 것이다`() {
        // **빈 화면에서 시작하기가 특히 어려운 언어들이다** (#394). 시작 코드가 없으면
        // 문법 오류부터 만난다.
        create("14-umjunsik-echo.json")

        mockMvc.perform(get("/api/v1/problems/umjunsik-echo"))
            .andExpect(jsonPath("$.runtimes[0].template").value("어떻게\n\n\n이 사람이름이냐ㅋㅋ\n"))
    }

    private fun create(file: String) {
        val body = Files.readString(seedDir().resolve(file))
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isCreated)
    }

    /** 시험은 `apps/api` 에서 돈다. 시드는 저장소 뿌리에 있다. */
    private fun seedDir(): Path {
        var dir = Path.of("").toAbsolutePath()
        while (!Files.exists(dir.resolve("scripts/seed-problems"))) {
            dir = dir.parent ?: error("scripts/seed-problems 를 찾지 못했습니다")
        }
        return dir.resolve("scripts/seed-problems")
    }
}
