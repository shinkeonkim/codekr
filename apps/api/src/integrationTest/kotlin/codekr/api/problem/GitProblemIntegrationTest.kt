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
     * **시드는 파일을 만들 수 있어야 한다** (#716, #730).
     *
     * 전에는 시드까지 `git` 으로 시작해야 해서 작업 트리에 파일을 만들 방법이 없었고,
     * 그래서 이 유형이 "빈 커밋을 몇 개 만들었나" 밖에 못 물었다. 시드와 정답은
     * **문제가 소유하고 어드민이 쓴다** — SQL 이 스키마·시드를 자유롭게 쓰는 것과 같다.
     */
    @Test
    fun `시드가 셸로 파일을 만들 수 있다`() {
        create(seed = "printf 'x' > app.py\\ngit add app.py\\ngit commit -q -m 첫커밋", slug = "seed-shell")
            .andExpect(status().isCreated)
    }

    @Test
    fun `정답도 셸을 쓸 수 있다`() {
        create(answer = "printf 'y' > app.py\\ngit add app.py", slug = "answer-shell")
            .andExpect(status().isCreated)
    }

    /**
     * **네트워크를 부르는 것은 등록에서 막는다** (#730).
     *
     * 샌드박스가 이미 막지만(#47), 거기서 막히면 사용자는 **출제자가 넣은 시드가
     * 실패한 것**을 자기 잘못으로 본다. 화면에는 채점 오류만 뜬다.
     */
    @Test
    fun `시드가 저장소를 받아 오려 하면 거부한다`() {
        create(seed = "git clone https://example.com/x.git", slug = "seed-clone")
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `셸로 내려받으려 해도 거부한다`() {
        create(seed = "curl -s https://example.com/x.sh", slug = "seed-curl")
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `원격을 더하는 것도 거부한다`() {
        // 그 줄 자체는 안 나가지만, 뒤이어 나가는 명령을 부르게 된다.
        create(seed = "git remote add origin https://example.com/x.git", slug = "seed-remote")
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `설명하는 주석은 명령이 아니다`() {
        // `# git clone 을 쓰지 마세요` 를 낱말이 아니라 문자열로 찾으면 이것이 걸린다.
        create(seed = "# git clone 은 여기서 못 씁니다\\ngit commit -q --allow-empty -m base", slug = "seed-comment")
            .andExpect(status().isCreated)
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
