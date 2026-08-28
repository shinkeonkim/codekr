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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 개념 퀴즈 (#650).
 *
 * **가장 중요한 것은 정답이 새지 않는 것이다.** 채점이 값 비교라, 응답 어딘가에 정답이
 * 실리면 개발자 도구를 여는 것만으로 문제가 무너진다 — 코드 문제에는 없던 위험이다.
 */
class QuizIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "운영자", setOf(UserRole.ADMIN))),
        )
        userToken = tokenProvider.issueAccessToken(
            userRepository.save(User("user@codekr.dev", "x", "푸는 사람", setOf(UserRole.USER))),
        )
    }

    private fun createQuiz(
        slug: String = "osi-layer",
        answerType: String = "SINGLE",
        choices: String = """
            [{"content":"1계층","correct":false},{"content":"4계층","correct":true},
             {"content":"7계층","correct":false}]
        """.trimIndent(),
        answers: String = "[]",
        extra: String = "",
    ): String {
        val body = """
            {"slug":"$slug","title":"TCP 는 몇 계층인가","category":"NETWORK","problemKind":"QUIZ",
             "description":"OSI 7계층에서 TCP 가 속한 계층을 고르시오.","published":true,
             "quizSpec":{"answerType":"$answerType","explanation":"TCP 는 전송 계층이다.",
                         "choices":$choices,"answers":$answers$extra}}
        """.trimIndent()
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content(body),
        ).andExpect(status().isCreated)
        return slug
    }

    /**
     * **정답과 해설이 문제 상세에 실리지 않는다.**
     *
     * `QuizViewResponse` 에 그 자리를 만들지 않은 것이 유일한 방어라, 여기서 문자열째로
     * 확인한다 — 필드를 하나 더 넣는 실수는 눈으로 리뷰할 때 지나가기 쉽다.
     */
    @Test
    fun `문제 상세에 정답도 해설도 실리지 않는다`() {
        val slug = createQuiz()

        val body = mockMvc.perform(get("/api/v1/problems/$slug"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quiz.answerType").value("SINGLE"))
            .andExpect(jsonPath("$.quiz.choices.length()").value(3))
            .andExpect(jsonPath("$.quiz.choices[0].content").value("1계층"))
            // 보기에 정답 표시가 없어야 한다.
            .andExpect(jsonPath("$.quiz.choices[0].correct").doesNotExist())
            .andReturn().response.contentAsString

        assertFalse(body.contains("correct"), "응답에 정답 표시가 남아 있습니다")
        assertFalse(body.contains("전송 계층"), "응답에 해설이 남아 있습니다")
    }

    @Test
    fun `정답을 고르면 맞고 해설이 그때 나온다`() {
        val slug = createQuiz()

        mockMvc.perform(
            post("/api/v1/problems/$slug/quiz")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"selected":[2]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.correct").value(true))
            .andExpect(jsonPath("$.explanation").value("TCP 는 전송 계층이다."))
    }

    @Test
    fun `틀려도 해설은 나온다`() {
        val slug = createQuiz()

        mockMvc.perform(
            post("/api/v1/problems/$slug/quiz")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"selected":[1]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.correct").value(false))
            // **틀린 사람에게 더 필요하다.** 4지선다는 판정만으로 배울 것이 없다.
            .andExpect(jsonPath("$.explanation").value("TCP 는 전송 계층이다."))
    }

    /**
     * 낸 답이 제출로 남되 **공개되지 않는다.**
     *
     * 다른 유형에서 `sourceCode` 는 풀이라 공개가 서로에게 배울 거리가 되지만(#33),
     * 퀴즈에서는 그것이 **정답 그 자체**다.
     */
    @Test
    fun `퀴즈 제출은 비공개로 남는다`() {
        val slug = createQuiz()

        mockMvc.perform(
            post("/api/v1/problems/$slug/quiz")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"selected":[2]}"""),
        ).andExpect(status().isOk)

        val visibility = jdbcOfBase.sql("SELECT visibility FROM submissions ORDER BY id DESC LIMIT 1")
            .query(String::class.java).single()
        assertEquals("PRIVATE", visibility)

        val runtimeId = jdbcOfBase.sql("SELECT runtime_id FROM submissions ORDER BY id DESC LIMIT 1")
            .query(String::class.java).single()
        assertEquals("quiz", runtimeId)
    }

    @Test
    fun `단답은 받아 주기로 한 답 중 하나면 맞는다`() {
        val slug = createQuiz(
            slug = "transport-protocol",
            answerType = "SHORT",
            choices = "[]",
            answers = """["TCP","전송 제어 프로토콜"]""",
        )

        // **한 번만 낸다.** 제출 간격이 30초라(#189) 여러 답을 잇달아 내려면 시험이
        // 분 단위로 길어진다. 정규화 규칙(대소문자·공백·동의어)은 `QuizGraderTest` 가
        // 갈래마다 맞는 답과 틀린 답을 넣어 덮는다 — 여기서 볼 것은 **그것이 실제
        // 경로에 붙어 있는가**다.
        mockMvc.perform(
            post("/api/v1/problems/$slug/quiz")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"text":"  tcp "}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.correct").value(true))
    }

    /**
     * **이 시험이 이 유형의 뼈대를 확인한다.**
     *
     * 퀴즈는 실행기를 쓰지 않지만, 결과는 **채점기가 낸 것과 같은 길**(`codekr:events`)로
     * 흘려보낸다. 그래야 판정 기록·활동·점수·문제 통계(`JudgeResultRecorder`)와
     * 실시간 중계(`/ws/submissions`)가 **새 경로 없이** 붙는다.
     *
     * 그 길이 끊기면 화면에는 "채점 중" 만 남고, 푼 기록도 활동도 남지 않는다 —
     * 그런데 제출 요청 자체는 200 이라 **아무도 모른다.**
     */
    @Test
    fun `채점 결과가 채점기와 같은 길로 흘러 제출에 기록된다`() {
        val slug = createQuiz()

        mockMvc.perform(
            post("/api/v1/problems/$slug/quiz")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"selected":[2]}"""),
        ).andExpect(status().isOk)

        // 발행은 Pub/Sub 이라 기록이 응답보다 늦다. 고정 시간을 자면 느린 기계에서 깨진다.
        val verdict = await { row("SELECT verdict FROM submissions ORDER BY id DESC LIMIT 1") }
        assertEquals("ACCEPTED", verdict)
        assertEquals("COMPLETED", row("SELECT status FROM submissions ORDER BY id DESC LIMIT 1"))
        // **푼 문제로 센다.** 점수는 난이도가 없어 0 이지만 행은 남는다 (#195).
        assertTrue(
            jdbcOfBase.sql("SELECT count(*) FROM user_problem_scores").query(Int::class.java).single() == 1,
            "푼 문제로 세지 않았습니다",
        )
        assertEquals(0, jdbcOfBase.sql("SELECT score FROM user_problem_scores").query(Int::class.java).single())
    }

    private fun row(sql: String): String? =
        jdbcOfBase.sql(sql).query(String::class.java).optional().orElse(null)

    private fun await(read: () -> String?): String? {
        repeat(50) {
            read()?.takeIf { value -> value != "PENDING" }?.let { return it }
            Thread.sleep(100)
        }
        return read()
    }

    /**
     * **어드민 편집 화면은 정답을 받아야 한다.**
     *
     * 푸는 사람에게 주지 않는 것과 반대다 — 여기서 빠지면 문제를 한 번 고칠 때마다
     * 정답 표시와 해설을 **다시 채워야** 하고, 그것을 잊으면 조용히 사라진다.
     */
    @Test
    fun `어드민 상세에는 정답과 해설이 온다`() {
        createQuiz()
        val id = jdbcOfBase.sql("SELECT id FROM problems ORDER BY id DESC LIMIT 1")
            .query(Long::class.java).single()

        mockMvc.perform(
            get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quizSpec.answerType").value("SINGLE"))
            .andExpect(jsonPath("$.quizSpec.choices[1].correct").value(true))
            .andExpect(jsonPath("$.quizSpec.explanation").value("TCP 는 전송 계층이다."))
    }

    /**
     * 보기를 다시 저장하면 **통째로 갈린다** (#650, #652 에서 발견).
     *
     * `(problem_id, seq)` 에 유니크 제약이 있는데 JPA 는 한 flush 안에서 INSERT 를
     * DELETE 보다 먼저 낼 수 있다 — 그러면 **수정할 때만 500 이 난다.**
     * 등록만 시험하면 드러나지 않아서, #652 에서 같은 모양을 만들다 잡았다.
     * #560 이 이미 겪은 자리다.
     */
    @Test
    fun `보기를 수정하면 통째로 갈린다`() {
        createQuiz()
        val id = jdbcOfBase.sql("SELECT id FROM problems ORDER BY id DESC LIMIT 1")
            .query(Long::class.java).single()

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/admin/problems/$id")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"slug":"osi-layer","title":"TCP 는 몇 계층인가","category":"NETWORK",
                     "problemKind":"QUIZ","description":"설명","published":true,
                     "quizSpec":{"answerType":"SINGLE","explanation":"고친 해설",
                       "choices":[{"content":"3계층","correct":false},
                                  {"content":"4계층","correct":true}]}}
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk)

        val rows = jdbcOfBase.sql("SELECT seq FROM problem_quiz_choices WHERE problem_id = :id ORDER BY seq")
            .param("id", id).query(Int::class.java).list()
        assertEquals(listOf(1, 2), rows, "번호가 1부터 다시 매겨져야 합니다")
    }

    @Test
    fun `로그인하지 않으면 낼 수 없다`() {
        val slug = createQuiz()

        mockMvc.perform(
            post("/api/v1/problems/$slug/quiz")
                .contentType(MediaType.APPLICATION_JSON).content("""{"selected":[2]}"""),
        ).andExpect(status().isUnauthorized)
    }

    /** 코드로 낼 문제에 답만 보내는 경로가 열려 있으면 안 된다. */
    @Test
    fun `퀴즈가 아닌 문제에는 이 경로를 쓸 수 없다`() {
        val body = """
            {"slug":"a-plus-b","title":"A+B","category":"ALGORITHM","problemKind":"JUDGE_STDIO",
             "description":"두 수를 더한다","published":true,
             "testcases":[{"seq":1,"input":"1 2","expectedOutput":"3","visibility":"PUBLIC"}]}
        """.trimIndent()
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content(body),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/problems/a-plus-b/quiz")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"selected":[1]}"""),
        ).andExpect(status().isBadRequest)
    }

    /**
     * **퀴즈에는 난이도를 매기지 않는다** (#650).
     *
     * 점수는 난이도에서 나오므로(#195) 그것이 곧 "랭킹 합에 넣지 않는다" 가 된다.
     * 랭킹 계산에 손대지 않고 얻는 결론이라, 이 규칙이 풀리면 조용히 점수가 붙는다.
     */
    @Test
    fun `퀴즈에 난이도를 매기면 거부한다`() {
        val body = """
            {"slug":"rated-quiz","title":"난이도 붙인 퀴즈","category":"NETWORK","problemKind":"QUIZ",
             "description":"설명","published":true,"difficulty":"BRONZE_5",
             "quizSpec":{"answerType":"SINGLE",
                         "choices":[{"content":"가","correct":true},{"content":"나","correct":false}]}}
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content(body),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `정답인 보기가 없으면 거부한다`() {
        val body = """
            {"slug":"no-answer","title":"정답 없는 퀴즈","category":"NETWORK","problemKind":"QUIZ",
             "description":"설명","published":true,
             "quizSpec":{"answerType":"SINGLE",
                         "choices":[{"content":"가","correct":false},{"content":"나","correct":false}]}}
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content(body),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `퀴즈가 아닌 문제에 퀴즈 스펙을 실으면 거부한다`() {
        val body = """
            {"slug":"mixed","title":"섞인 문제","category":"ALGORITHM","problemKind":"JUDGE_STDIO",
             "description":"설명","published":false,
             "quizSpec":{"answerType":"SINGLE",
                         "choices":[{"content":"가","correct":true},{"content":"나","correct":false}]}}
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON).content(body),
        ).andExpect(status().isBadRequest)
    }

}
