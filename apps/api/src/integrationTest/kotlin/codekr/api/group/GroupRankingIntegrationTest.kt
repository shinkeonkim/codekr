package codekr.api.group

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.queue.message.JudgeEventMessage
import codekr.api.submission.entity.Verdict
import codekr.api.submission.service.JudgeResultRecorder
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 그룹 안 랭킹 (#402, #240 7단계).
 *
 * **소속 안 랭킹(#399)과 같은 구조다.** 다른 것은 누가 볼 수 있느냐뿐이다 —
 * 그룹 명단은 그 안의 일이므로 랭킹도 멤버만 본다.
 */
class GroupRankingIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var recorder: JudgeResultRecorder

    private val tokens = mutableMapOf<Long, String>()
    private var owner: Long = 0
    private var member: Long = 0
    private var outsider: Long = 0
    private var groupId: Long = 0

    @BeforeEach
    fun setUp() {
        owner = user("owner", "방장")
        member = user("member", "친구")
        outsider = user("outsider", "남")
        groupId = group(owner)
        join(groupId, member)

        problem(1, level = 20) // 694점
        problem(2, level = 11) // 93점
    }

    @Test
    fun `그룹 안에서 1위부터 다시 매긴다`() {
        // 밖의 사람이 아무리 잘해도 이 순위표에는 없다 — 그것이 "우리 스터디에서 2등" 의 뜻이다.
        accept(outsider, 1)
        accept(member, 2)

        mockMvc.perform(get("/api/v1/groups/$groupId/rankings").withUser(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].nickname").value("친구"))
            .andExpect(jsonPath("$.content[0].rank").value(1))
            .andExpect(jsonPath("$.content[1].nickname").value("방장"))
            .andExpect(jsonPath("$.content[1].rank").value(2))
    }

    @Test
    fun `아직 못 푼 멤버도 순위표에 있다`() {
        // #391 이 전체 순위표에서 정한 것이 여기서도 같다 — 시작점이 안 보이면
        // 올라갈 곳도 안 보인다.
        mockMvc.perform(get("/api/v1/groups/$groupId/rankings").withUser(member))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].score").value(0))
    }

    @Test
    fun `멤버가 아니면 볼 수 없다`() {
        /*
          **그룹의 명단이 곧 그 랭킹이다.** 공개로 두면 그룹 id 하나로 누가 있는지
          전부 읽을 수 있다 — #401 이 명단을 멤버만 보게 한 것이 무너진다.
        */
        mockMvc.perform(get("/api/v1/groups/$groupId/rankings").withUser(outsider))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `로그인하지 않으면 볼 수 없다`() {
        mockMvc.perform(get("/api/v1/groups/$groupId/rankings"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `나가면 순위표에서 사라진다`() {
        accept(member, 1)
        jdbcClient.sql("DELETE FROM group_members WHERE group_id = :g AND user_id = :u")
            .param("g", groupId).param("u", member).update()

        mockMvc.perform(get("/api/v1/groups/$groupId/rankings").withUser(owner))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].nickname").value("방장"))
    }

    @Test
    fun `해산한 그룹의 순위는 없다`() {
        jdbcClient.sql("UPDATE groups SET deleted_at = now() WHERE id = :id").param("id", groupId).update()

        mockMvc.perform(get("/api/v1/groups/$groupId/rankings").withUser(owner))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `탈퇴한 사람은 그룹 순위표에서도 빠진다`() {
        // 전체 순위표의 규칙(#207)이 여기서도 같다.
        accept(member, 1)
        jdbcClient.sql("UPDATE users SET withdrawn_at = now() WHERE id = :id").param("id", member).update()

        mockMvc.perform(get("/api/v1/groups/$groupId/rankings").withUser(owner))
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `지표를 바꾸면 정렬이 바뀐다`() {
        // 사람 랭킹의 축을 그대로 쓴다 — 여기서만 다른 규칙을 만들지 않는다.
        accept(owner, 1) // 694점 한 문제
        accept(member, 2) // 93점 한 문제
        accept(member, 1) // + 694점 → 두 문제

        mockMvc.perform(
            get("/api/v1/groups/$groupId/rankings").param("metric", "SOLVED_COUNT").withUser(owner),
        )
            .andExpect(jsonPath("$.content[0].nickname").value("친구"))
            .andExpect(jsonPath("$.content[0].solvedCount").value(2))
    }

    private fun user(prefix: String, nickname: String): Long {
        val saved = userRepository.save(User("$prefix@codekr.dev", "x", nickname, setOf(UserRole.USER)))
        tokens[saved.id] = tokenProvider.issueAccessToken(saved)
        return saved.id
    }

    private fun MockHttpServletRequestBuilder.withUser(userId: Long): MockHttpServletRequestBuilder =
        header("Authorization", "Bearer ${tokens[userId]}")

    private fun group(ownerId: Long): Long {
        val id = jdbcClient.sql(
            "INSERT INTO groups (name, owner_id, invite_token) VALUES ('스터디', :o, 'tok') RETURNING id",
        ).param("o", ownerId).query(Long::class.java).single()
        join(id, ownerId)
        return id
    }

    private fun join(groupId: Long, userId: Long) {
        jdbcClient.sql("INSERT INTO group_members (group_id, user_id) VALUES (:g, :u)")
            .param("g", groupId).param("u", userId).update()
    }

    private fun problem(id: Long, level: Int) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, 'p-' || :id, '문제 ' || :id, 'ALGORITHM', :level, '설명', true)
            """,
        ).param("id", id).param("level", level).update()
    }

    private fun accept(userId: Long, problemId: Long) {
        val submissionId = jdbcClient.sql(
            """
            INSERT INTO submissions (user_id, problem_id, runtime_id, source_code, status, kind)
            VALUES (:u, :p, 'python:3.12', 'print(3)', 'PENDING', 'USER') RETURNING id
            """,
        ).param("u", userId).param("p", problemId).query(Long::class.java).single()

        recorder.record(
            JudgeEventMessage(
                type = JudgeEventMessage.TYPE_COMPLETED,
                submissionId = submissionId,
                verdict = Verdict.ACCEPTED.name,
                passedCount = 1,
                totalCount = 1,
            ),
        )
    }
}
