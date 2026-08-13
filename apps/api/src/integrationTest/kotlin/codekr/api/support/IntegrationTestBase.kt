package codekr.api.support

import org.junit.jupiter.api.BeforeEach
import codekr.api.contest.scoreboard.ScoreboardCache
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 실제 PostgreSQL/Redis 컨테이너를 띄우는 통합 테스트의 공통 기반.
 * 무겁기 때문에 `integrationTest` 소스셋에만 존재하며 기본 `test` 태스크에서는 실행되지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegrationTestBase {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Autowired
    protected lateinit var jdbcOfBase: JdbcClient

    /** 테스트 클래스 사이에 데이터가 새지 않도록 매 테스트 전에 전체를 비운다. */
    @Autowired private lateinit var scoreboardCache: ScoreboardCache

    @Autowired private lateinit var redisTemplate: StringRedisTemplate

    @Autowired private lateinit var badgeCatalog: codekr.api.ranking.badge.BadgeCatalog

    @BeforeEach
    fun truncateAll() {
        // 순위표 캐시는 애플리케이션 전체가 공유한다. 비우지 않으면 앞 시험의 결과가 남는다.
        scoreboardCache.clear()
        // 토큰 무효화 표시는 Redis 에 남는다 (#140, #315). 시험마다 사용자 id 가 1 부터 다시 시작하므로,
        // 비우지 않으면 앞 시험에서 탈퇴한 id 가 뒤 시험의 사용자를 막는다.
        redisTemplate.keys("codekr:revoked:*").forEach(redisTemplate::delete)
        jdbcClient.sql(
            """
            TRUNCATE comments, posts, submission_views, problem_collection_items, problem_collections,
                     contest_registrations, contest_problems, contests,
                     submission_testcase_results, submissions, problem_runtime_limits,
                     problem_files,
                     problem_difficulty_votes,
                     problem_reports,
                     user_score_history,
                     problem_sql_specs,
                     problem_nosql_specs,
                     problem_templates, problem_testcases, problems,
                     notifications, admin_audit_logs, rejudge_batches, user_daily_activity,
                     problem_allowed_runtimes,
                     problem_tags, problem_credits, tags,
                     user_problem_scores, user_badges, user_roles, user_suspensions,
                     email_verifications, password_resets, term_agreements,
                     group_members, groups,
                     user_affiliations, affiliation_domains, affiliations,
                     user_emails, post_attachments, users
            RESTART IDENTITY CASCADE
            """,
        ).update()

        /*
            약관 판은 **마이그레이션이 넣은 것**이라 비우지 않는다 (#235) — 비우면 가입
            시험이 동의할 판을 잃는다. 대신 시험이 넣은 개정만 걷는다.
        */
        jdbcClient.sql("DELETE FROM term_documents WHERE version <> '1.0'").update()

        /*
            뱃지 정의도 마이그레이션이 넣은 것이다 (#201) — 비우지 않고, 시험이 고친
            문구·노출만 되돌린다. 캐시도 함께 비운다.
        */
        jdbcClient.sql(
            """
            UPDATE badges SET label = seed.label, description = seed.description,
                              visible = true, sort_order = seed.sort_order
            FROM (VALUES
                ('FIRST_ACCEPT', '첫 정답', '처음으로 문제를 맞혔습니다', 10),
                ('STREAK_7', '일주일 연속', '7일 연속으로 문제를 풀었습니다', 20)
            ) AS seed(code, label, description, sort_order)
            WHERE badges.code = seed.code
            """,
        ).update()
        jdbcClient.sql("DELETE FROM badges WHERE code NOT LIKE 'CATEGORY_10_%' AND code NOT IN ('FIRST_ACCEPT','STREAK_7','STREAK_30','FIRST_SOLVER')").update()
        // 규칙도 마이그레이션이 넣은 것이다 (#202) — 시험이 넣은 것만 걷고 스위치를 되돌린다.
        jdbcClient.sql(
            "DELETE FROM badge_rules WHERE rule_key NOT IN ('FIRST_ACCEPT','FIRST_SOLVER','CATEGORY_10','STREAK_7','STREAK_30')",
        ).update()
        jdbcClient.sql("UPDATE badge_rules SET enabled = true").update()
        jdbcClient.sql("TRUNCATE badge_awards_log").update()
        badgeCatalog.invalidate()
    }


    /**
     * 가입 본문. **필수 약관에 동의해야 가입이 된다** (#235).
     *
     * 시행 중인 약관 id 를 그때그때 읽어 넣는다 — 시드가 넣은 판의 id 를 시험이 알 수 없다.
     */
    protected fun signupBody(email: String, password: String, nickname: String): String {
        val ids = jdbcOfBase.sql("SELECT id FROM term_documents WHERE effective_at <= now()")
            .query(Long::class.java).list()
        return """{"email":"$email","password":"$password","nickname":"$nickname","agreedTermIds":${ids}}"""
    }

    companion object {
        private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("codekr")
            .withUsername("codekr")
            .withPassword("codekr")

        private val redis = GenericContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)

        init {
            postgres.start()
            redis.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }
}
