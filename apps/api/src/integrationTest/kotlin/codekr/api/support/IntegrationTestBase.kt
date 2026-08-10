package codekr.api.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
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

    /** 테스트 클래스 사이에 데이터가 새지 않도록 매 테스트 전에 전체를 비운다. */
    @BeforeEach
    fun truncateAll() {
        jdbcClient.sql(
            """
            TRUNCATE submission_testcase_results, submissions, problem_runtime_limits,
                     problem_templates, problem_testcases, problems,
                     notifications, notification_mutes, rejudge_batches, user_daily_activity, user_roles, users
            RESTART IDENTITY CASCADE
            """,
        ).update()
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
