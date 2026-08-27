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

/**
 * Git 문제 (#654).
 *
 * **Redis 와 모양이 같다.** 여기서 확인하는 것은 그 뼈대가 붙었는지와, Git 만의 규칙 —
 * 시드·정답에 git 아닌 명령이 들어오지 않는 것이다.
 */
class GitProblemIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "운영자", setOf(UserRole.ADMIN))),
        )
    }

    private fun create(
        seed: String = "git commit -q --allow-empty -m base",
        answer: String = "git commit -q --allow-empty -m fix",
        slug: String = "undo-last-commit",
        kind: String = "JUDGE_GIT",
    ) = mockMvc.perform(
        post("/api/v1/admin/problems")
            .header("Authorization", "Bearer $adminToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"slug":"$slug","title":"마지막 커밋 되돌리기","category":"ALGORITHM","problemKind":"$kind",
                 "description":"마지막 커밋을 지우고 그 앞으로 돌아가시오.","published":true,
                 "gitSpec":{"seedCommands":"$seed","answerCommands":"$answer",
                            "verifyCommands":"git log --format='%T %s'"}}
                """.trimIndent(),
            ),
    )

    @Test
    fun `시드와 정답과 확인 명령이 있으면 등록된다`() {
        create().andExpect(status().isCreated)
    }

    /**
     * **git 아닌 명령은 등록에서 막는다.**
     *
     * 하네스도 같은 규칙으로 막지만, 거기서 막히면 사용자는 **출제자가 넣은 시드가
     * 실패한 것**을 자기 잘못으로 본다.
     */
    @Test
    fun `시드에 git 아닌 명령이 있으면 거부한다`() {
        create(seed = "rm -rf /").andExpect(status().isBadRequest)
    }

    @Test
    fun `정답에 git 아닌 명령이 있으면 거부한다`() {
        create(answer = "echo 안녕").andExpect(status().isBadRequest)
    }

    @Test
    fun `Git 문제가 아닌데 스펙을 실으면 거부한다`() {
        create(kind = "JUDGE_STDIO", slug = "mixed").andExpect(status().isBadRequest)
    }

    /** 이 유형으로 풀 수 있는 실행 환경만 내린다 (#60) — 사용자가 언어를 고르지 않는다. */
    @Test
    fun `공개 상세에는 Git 런타임만 내린다`() {
        create().andExpect(status().isCreated)

        val body = mockMvc.perform(get("/api/v1/problems/undo-last-commit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.problemKind").value("JUDGE_GIT"))
            .andExpect(jsonPath("$.runtimes.length()").value(1))
            .andExpect(jsonPath("$.runtimes[0].id").value("git:2"))
            .andReturn().response.contentAsString

        // 정답 명령이 새면 문제가 무너진다 — 히든 테스트케이스와 같은 규칙이다.
        assert(!body.contains("--allow-empty")) { "정답 명령이 응답에 남아 있습니다" }
    }

    @Test
    fun `어드민 상세에는 시드와 정답이 온다`() {
        create().andExpect(status().isCreated)
        val id = jdbcOfBase.sql("SELECT id FROM problems ORDER BY id DESC LIMIT 1")
            .query(Long::class.java).single()

        mockMvc.perform(
            get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gitSpec.seedCommands").value("git commit -q --allow-empty -m base"))
            .andExpect(jsonPath("$.gitSpec.answerCommands").value("git commit -q --allow-empty -m fix"))
            .andExpect(jsonPath("$.gitSpec.verifyCommands").value("git log --format='%T %s'"))
    }
}
