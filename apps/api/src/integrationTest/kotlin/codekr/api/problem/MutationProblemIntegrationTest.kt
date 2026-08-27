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
 * 테스트를 쓰는 문제 (#652).
 *
 * **가장 위험한 것은 구현이 새는 것이다.** 버그를 심은 구현이 보이면 무엇을 확인해야
 * 하는지가 곧 답이 되어 문제가 무너진다 — #525 가 SQL 에서 짚은 자리와 같다.
 */
class MutationProblemIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "운영자", setOf(UserRole.ADMIN))),
        )
    }

    private val reference = "def average(xs):\\n    return sum(xs) / len(xs)"
    private val mutant = "def average(xs):\\n    return sum(xs) / len(xs) - 1"

    private fun create(
        slug: String = "write-average-tests",
        mutants: String = """[{"label":"하나 모자라게 나눈다","source":"$mutant"}]""",
        reference: String = this.reference,
        published: Boolean = true,
        kind: String = "JUDGE_MUTATION",
    ) = mockMvc.perform(
        post("/api/v1/admin/problems")
            .header("Authorization", "Bearer $adminToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"slug":"$slug","title":"평균 함수의 시험을 쓰시오","category":"ALGORITHM",
                 "problemKind":"$kind","description":"average 를 지킬 시험을 쓰세요.",
                 "published":$published,
                 "mutationSpec":{"referenceSource":"$reference","mutants":$mutants}}
                """.trimIndent(),
            ),
    )

    @Test
    fun `올바른 구현과 버그 심은 구현이 있으면 등록된다`() {
        create().andExpect(status().isCreated)
    }

    /**
     * **버그 심은 구현도, 그 이름표도 새면 안 된다.**
     *
     * 이름표는 "무엇을 심었는가" 라서 그것만으로도 무엇을 확인해야 하는지가 드러난다.
     */
    @Test
    fun `공개 상세에 구현이 실리지 않는다`() {
        create().andExpect(status().isCreated)

        val body = mockMvc.perform(get("/api/v1/problems/write-average-tests"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.problemKind").value("JUDGE_MUTATION"))
            .andExpect(jsonPath("$.runtimes[0].id").value("mutation:python"))
            .andReturn().response.contentAsString

        assert(!body.contains("sum(xs)")) { "구현이 응답에 남아 있습니다" }
        assert(!body.contains("하나 모자라게")) { "이름표가 응답에 남아 있습니다" }
    }

    /**
     * **버그 심은 구현이 없으면 문제가 아니다.**
     *
     * 아무것도 확인하지 않는 시험이 통과하고, 그것은 오류를 내지 않는다 —
     * 정규식(#653)에서 "맞으면 안 되는 문자열" 이 없던 것과 같은 자리다.
     */
    @Test
    fun `버그 심은 구현이 없으면 공개할 수 없다`() {
        create(mutants = "[]").andExpect(status().isBadRequest)
    }

    /**
     * **올바른 구현과 같은 것을 뮤턴트로 넣으면 아무도 못 맞힌다.**
     *
     * 그 구현은 정의상 시험을 통과하는데 기대값은 실패이므로 어떤 시험을 내도 틀린다 —
     * 그리고 오류가 아니라 "정답률 0%" 로만 보인다.
     */
    @Test
    fun `올바른 구현과 같은 뮤턴트는 거부한다`() {
        create(mutants = """[{"source":"$reference"}]""").andExpect(status().isBadRequest)
    }

    @Test
    fun `테스트 작성 문제가 아닌데 구현을 실으면 거부한다`() {
        create(slug = "mixed", kind = "JUDGE_STDIO").andExpect(status().isBadRequest)
    }

    /** 초안이면 아직 덜 채워도 된다 — 만들다 저장하는 흐름이 막히면 안 된다. */
    @Test
    fun `초안은 뮤턴트 없이도 저장된다`() {
        create(slug = "draft-mutation", mutants = "[]", published = false)
            .andExpect(status().isCreated)
    }

    /** 어드민은 고쳐야 하므로 받는다 — 푸는 사람과 반대다. */
    @Test
    fun `어드민 상세에는 구현과 이름표가 온다`() {
        create().andExpect(status().isCreated)
        val id = jdbcOfBase.sql("SELECT id FROM problems ORDER BY id DESC LIMIT 1")
            .query(Long::class.java).single()

        mockMvc.perform(
            get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mutationSpec.mutants.length()").value(1))
            .andExpect(jsonPath("$.mutationSpec.mutants[0].label").value("하나 모자라게 나눈다"))
            .andExpect(jsonPath("$.mutationSpec.referenceSource").exists())
    }

    /**
     * 구현을 다시 저장하면 **통째로 갈아 끼운다.**
     *
     * 번호가 바뀌면 그것은 다른 구현이고, 무엇이 무엇의 개정인지 짐작하면 **판정 순서가
     * 어긋난다** — 그 어긋남은 오류를 내지 않고 정답률로만 드러난다.
     */
    @Test
    fun `수정하면 구현이 통째로 갈린다`() {
        create().andExpect(status().isCreated)
        val id = jdbcOfBase.sql("SELECT id FROM problems ORDER BY id DESC LIMIT 1")
            .query(Long::class.java).single()

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/admin/problems/$id")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"slug":"write-average-tests","title":"평균 함수의 시험을 쓰시오",
                     "category":"ALGORITHM","problemKind":"JUDGE_MUTATION",
                     "description":"설명","published":true,
                     "mutationSpec":{"referenceSource":"$reference",
                       "mutants":[{"source":"def average(xs):\\n    return 0"},
                                  {"source":"def average(xs):\\n    return max(xs)"}]}}
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk)

        val rows = jdbcOfBase.sql("SELECT seq FROM problem_mutants WHERE problem_id = :id ORDER BY seq")
            .param("id", id).query(Int::class.java).list()
        assert(rows == listOf(1, 2)) { "번호가 1부터 다시 매겨져야 합니다: $rows" }
    }
}
