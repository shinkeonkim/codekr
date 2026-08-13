package codekr.api.contest

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.contest.entity.Contest
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

/** 대회 도메인과 생명주기 (#61). */
class ContestIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        val admin = userRepository.save(
            User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER)),
        )
        adminToken = tokenProvider.issueAccessToken(admin)
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        userToken = tokenProvider.issueAccessToken(user)

        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (1, 'two-sum', '두 수의 합', 'ALGORITHM', 1, '설명', true),
                   (2, 'three-sum', '세 수의 합', 'ALGORITHM', 5, '설명', true)
            """,
        ).update()
    }

    @Test
    fun `준비 중인 대회는 목록에도 상세에도 없다`() {
        createContest("hidden")

        mockMvc.perform(get("/api/v1/contests"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(0))

        // 존재 여부조차 알리지 않는다.
        mockMvc.perform(get("/api/v1/contests/hidden")).andExpect(status().isNotFound)
    }

    @Test
    fun `시작 전에는 참가자도 문제를 볼 수 없다`() {
        // #61 의 완료 조건이다. 등록만 하면 미리 볼 수 있다면 대회가 성립하지 않는다.
        val id = createContest("upcoming", startsIn = Duration.ofHours(1))
        publish(id)
        register("upcoming")

        mockMvc.perform(
            get("/api/v1/contests/upcoming").header("Authorization", "Bearer $userToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.phase").value("SCHEDULED"))
            .andExpect(jsonPath("$.registered").value(true))
            .andExpect(jsonPath("$.problems.length()").value(0))
    }

    @Test
    fun `진행 중에는 참가자만 문제를 본다`() {
        val id = createContest("running", startsIn = Duration.ofMinutes(-10))
        publish(id)

        // 등록하지 않은 사람에게는 보이지 않는다.
        mockMvc.perform(get("/api/v1/contests/running"))
            .andExpect(jsonPath("$.summary.phase").value("RUNNING"))
            .andExpect(jsonPath("$.problems.length()").value(0))

        register("running")

        mockMvc.perform(get("/api/v1/contests/running").header("Authorization", "Bearer $userToken"))
            .andExpect(jsonPath("$.problems.length()").value(2))
            // 대회 안에서는 A, B 로 부른다.
            .andExpect(jsonPath("$.problems[0].label").value("A"))
            .andExpect(jsonPath("$.problems[1].label").value("B"))
            .andExpect(jsonPath("$.problems[0].score").value(100))
    }

    @Test
    fun `종료 후에는 누구나 문제를 본다`() {
        val id = createContest("finished", startsIn = Duration.ofHours(-3), length = Duration.ofHours(2))
        publish(id)

        mockMvc.perform(get("/api/v1/contests/finished"))
            .andExpect(jsonPath("$.summary.phase").value("ENDED"))
            .andExpect(jsonPath("$.problems.length()").value(2))
    }

    @Test
    fun `진행 단계는 저장하지 않고 시각으로 판정한다`() {
        // 스케줄러가 상태를 옮기는 방식이면 스케줄러가 1분 늦는 순간 대회가 1분 늦게 시작한다.
        val id = createContest("time-based", startsIn = Duration.ofMinutes(-1))
        publish(id)

        val stored = jdbcClient.sql("SELECT status FROM contests WHERE id = :id")
            .param("id", id).query(String::class.java).single()
        assertEquals("PUBLISHED", stored, "저장되는 것은 운영자가 정한 상태뿐이어야 합니다")

        mockMvc.perform(get("/api/v1/contests/time-based"))
            .andExpect(jsonPath("$.summary.phase").value("RUNNING"))
    }

    @Test
    fun `동결 시각이 지나면 순위가 감춰진 것으로 표시된다`() {
        // 30분짜리 대회에 동결 30분이면 대회 내내 동결이므로, 90분짜리로 만든다.
        val id = createContest("frozen", startsIn = Duration.ofMinutes(-70), length = Duration.ofMinutes(90))
        publish(id)

        mockMvc.perform(get("/api/v1/contests/frozen"))
            .andExpect(jsonPath("$.summary.frozen").value(true))
    }

    @Test
    fun `최종 순위 공개는 종료 뒤에만 할 수 있다`() {
        val id = createContest("unfreeze", startsIn = Duration.ofMinutes(-10))
        publish(id)

        // 진행 중에 공개하면 동결의 의미가 없다.
        mockMvc.perform(post("/api/v1/admin/contests/$id/unfreeze").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `문제가 없으면 공개할 수 없다`() {
        // 빈 대회를 공개하면 참가자가 등록한 뒤 시작 시각에 아무것도 못 본다.
        val id = createContest("empty", problems = false)

        mockMvc.perform(
            put("/api/v1/admin/contests/$id/status")
                .param("status", "PUBLISHED")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `동결 시간이 대회 길이보다 길면 거부한다`() {
        mockMvc.perform(
            post("/api/v1/admin/contests")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("too-frozen", Duration.ofHours(1), Duration.ofMinutes(20), freezeMinutes = 30)),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `시작 전에는 등록할 수 있고 종료 후에는 못 한다`() {
        val upcoming = createContest("open-reg", startsIn = Duration.ofHours(1))
        publish(upcoming)
        mockMvc.perform(
            post("/api/v1/contests/open-reg/registrations").header("Authorization", "Bearer $userToken"),
        ).andExpect(status().isNoContent)

        // 두 번 눌러도 오류가 아니어야 한다.
        mockMvc.perform(
            post("/api/v1/contests/open-reg/registrations").header("Authorization", "Bearer $userToken"),
        ).andExpect(status().isNoContent)

        val ended = createContest("closed-reg", startsIn = Duration.ofHours(-3), length = Duration.ofHours(2))
        publish(ended)
        mockMvc.perform(
            post("/api/v1/contests/closed-reg/registrations").header("Authorization", "Bearer $userToken"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `제외한 문제는 제외됨으로 표시된다`() {
        val id = createContest("excluded", startsIn = Duration.ofMinutes(-10))
        publish(id)
        register("excluded")

        // 배정을 지우지 않는 이유는 그 문제로 낸 제출이 남아 있기 때문이다.
        mockMvc.perform(
            put("/api/v1/admin/contests/$id/problems/1/exclusion")
                .param("excluded", "true")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/contests/excluded").header("Authorization", "Bearer $userToken"))
            .andExpect(jsonPath("$.problems[0].excluded").value(true))
    }

    @Test
    fun `공개한 대회는 삭제할 수 없다`() {
        val id = createContest("published")
        publish(id)

        // 제출 이력이 딸려 있다. 취소를 써야 한다.
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/admin/contests/$id")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `대회 순번은 26 을 넘으면 두 글자가 된다`() {
        assertEquals("A", codekr.api.contest.service.ContestService.labelOf(1))
        assertEquals("Z", codekr.api.contest.service.ContestService.labelOf(26))
        assertEquals("AA", codekr.api.contest.service.ContestService.labelOf(27))
    }

    private fun createContest(
        slug: String,
        startsIn: Duration = Duration.ofHours(1),
        length: Duration = Duration.ofHours(2),
        problems: Boolean = true,
    ): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/contests")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(slug, startsIn, length, withProblems = problems)),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun publish(id: Long) {
        mockMvc.perform(
            put("/api/v1/admin/contests/$id/status")
                .param("status", "PUBLISHED")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isOk)
    }

    private fun register(slug: String) {
        mockMvc.perform(
            post("/api/v1/contests/$slug/registrations").header("Authorization", "Bearer $userToken"),
        ).andExpect(status().isNoContent)
    }

    private fun body(
        slug: String,
        startsIn: Duration,
        length: Duration,
        freezeMinutes: Int = Contest.DEFAULT_FREEZE_MINUTES,
        withProblems: Boolean = true,
    ): String {
        val startsAt = Instant.now().plus(startsIn)
        val problems = if (withProblems) {
            """[{"problemId": 1, "seq": 1, "score": 100}, {"problemId": 2, "seq": 2, "score": 200}]"""
        } else {
            "[]"
        }
        return """
            {
              "slug": "$slug", "title": "대회 $slug", "description": "설명",
              "startsAt": "$startsAt", "endsAt": "${startsAt.plus(length)}",
              "freezeMinutes": $freezeMinutes, "registrationOpenDuring": true,
              "problems": $problems
            }
        """.trimIndent()
    }
    @Test
    fun `진행 중인 대회는 고칠 수 없다`() {
        // **대회 중에 시작 시각이나 문제가 바뀌면** 이미 제출한 사람과 아닌 사람이
        // 다른 대회를 본 것이 된다 (#335). 화면이 아니라 **서버가** 막는다.
        val id = jdbcClient.sql(
            """
            INSERT INTO contests (slug, title, description, starts_at, ends_at, status,
                                  freeze_minutes, submission_cooldown_seconds)
            VALUES ('running-now', '진행 중', '', now() - interval '1 hour', now() + interval '1 hour',
                    'PUBLISHED', 0, 20)
            RETURNING id
            """,
        ).query(Long::class.java).single()

        mockMvc.perform(
            put("/api/v1/admin/contests/" + id).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"slug":"running-now","title":"몰래 바꾸기","description":"",
                     "startsAt":"2030-01-01T00:00:00Z","endsAt":"2030-01-02T00:00:00Z"}
                    """.trimIndent(),
                ),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `시작 전 대회는 고칠 수 있다`() {
        // 막히는 것은 **진행 중**뿐이다 — 준비 중·시작 전은 그대로 고쳐진다.
        val id = jdbcClient.sql(
            """
            INSERT INTO contests (slug, title, description, starts_at, ends_at, status,
                                  freeze_minutes, submission_cooldown_seconds)
            VALUES ('later', '나중 대회', '', now() + interval '1 day', now() + interval '2 days',
                    'PUBLISHED', 0, 20)
            RETURNING id
            """,
        ).query(Long::class.java).single()

        mockMvc.perform(
            put("/api/v1/admin/contests/" + id).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"slug":"later","title":"고친 제목","description":"",
                     "startsAt":"2030-01-01T00:00:00Z","endsAt":"2030-01-02T00:00:00Z"}
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk)
    }

}
