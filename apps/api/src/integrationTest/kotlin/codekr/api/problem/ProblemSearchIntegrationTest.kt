package codekr.api.problem

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.entity.ProblemTemplate
import codekr.api.problem.entity.ProblemTestcase
import codekr.api.problem.entity.TestcaseVisibility
import codekr.api.problem.repository.ProblemRepository
import codekr.api.support.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals

class ProblemSearchIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var problemRepository: ProblemRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun setUp() {
        problemRepository.saveAll(
            listOf(
                newProblem("two-sum", "두 수의 합", ProblemCategory.ALGORITHM, Difficulty.BRONZE_5, published = true),
                newProblem("join-basics", "조인 기초", ProblemCategory.SQL, Difficulty.GOLD_4, published = true),
                newProblem("draft", "미공개 문제", ProblemCategory.ALGORITHM, Difficulty.RUBY_1, published = false),
            ),
        )
    }

    @Test
    fun `공개된 문제만 목록에 노출된다`() {
        mockMvc.perform(get("/api/v1/problems"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    fun `카테고리와 키워드로 필터링한다`() {
        mockMvc.perform(get("/api/v1/problems").param("category", "SQL"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].slug").value("join-basics"))

        mockMvc.perform(get("/api/v1/problems").param("q", "두 수"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `티어로 필터링하고 난이도 순으로 정렬한다`() {
        mockMvc.perform(get("/api/v1/problems").param("tier", "GOLD"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].slug").value("join-basics"))
            .andExpect(jsonPath("$.content[0].difficulty").value("GOLD_4"))
            .andExpect(jsonPath("$.content[0].difficultyLabel").value("골드 4"))

        mockMvc.perform(get("/api/v1/problems").param("sort", "DIFFICULTY"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].slug").value("two-sum"))
    }

    @Test
    fun `상세 조회는 히든 테스트케이스를 노출하지 않는다`() {
        // 지연 로딩 컬렉션을 다루므로 트랜잭션 안에서 준비한다.
        transactionTemplate.executeWithoutResult {
            problemRepository.findBySlugAndDeletedAtIsNull("two-sum")!!.addTestcases(
                listOf(
                    ProblemTestcase(1, "1 2\n", "3\n", TestcaseVisibility.PUBLIC),
                    ProblemTestcase(2, "비밀 입력\n", "비밀 출력\n", TestcaseVisibility.HIDDEN),
                ),
            )
        }

        mockMvc.perform(get("/api/v1/problems/two-sum"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.examples.length()").value(1))
            .andExpect(jsonPath("$.examples[0].input").value("1 2\n"))
            // 런타임 개수는 정의 파일이 늘어나면 바뀐다 — 개수가 아니라 존재를 확인한다.
            .andExpect(jsonPath("$.runtimes[?(@.id == 'python:3.12')]").exists())
    }

    @Test
    fun `문제가 지정한 초기 코드가 런타임 기본 템플릿을 대체한다`() {
        transactionTemplate.executeWithoutResult {
            problemRepository.findBySlugAndDeletedAtIsNull("two-sum")!!.addTemplates(
                listOf(ProblemTemplate("python:3.12", "# 이 문제 전용 시작 코드\n")),
            )
        }

        mockMvc.perform(get("/api/v1/problems/two-sum"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runtimes[?(@.id == 'python:3.12')].template").value("# 이 문제 전용 시작 코드\n"))
            // 지정하지 않은 런타임은 레지스트리 기본 템플릿을 그대로 쓴다.
            .andExpect(jsonPath("$.runtimes[?(@.id == 'cpp:17')].template").isNotEmpty)
    }

    @Test
    fun `미공개 문제 상세는 404 다`() {
        mockMvc.perform(get("/api/v1/problems/draft")).andExpect(status().isNotFound)
    }

    @Test
    fun `번호로도 문제를 연다`() {
        // 사람은 문제를 "1000번" 이라고 부른다 (#204). 그렇게 부른 것을 주소창에 넣었을 때
        // 열리지 않으면 번호를 보여 주는 뜻이 없다.
        val id = problemRepository.findBySlugAndDeletedAtIsNull("two-sum")!!.id

        mockMvc.perform(get("/api/v1/problems/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.slug").value("two-sum"))
    }

    @Test
    fun `번호로 열어도 미공개는 404 다`() {
        // 번호로 여는 길이 공개 여부를 우회하면 안 된다.
        val id = problemRepository.findBySlugAndDeletedAtIsNull("draft")!!.id

        mockMvc.perform(get("/api/v1/problems/$id")).andExpect(status().isNotFound)
    }

    @Test
    fun `검색창에 번호를 넣으면 그 문제가 나온다`() {
        val id = problemRepository.findBySlugAndDeletedAtIsNull("two-sum")!!.id

        mockMvc.perform(get("/api/v1/problems").param("q", id.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].slug").value("two-sum"))
    }

    @Test
    fun `소프트 삭제된 문제는 목록과 상세에서 사라진다`() {
        transactionTemplate.executeWithoutResult {
            problemRepository.findBySlugAndDeletedAtIsNull("two-sum")!!.delete()
        }

        mockMvc.perform(get("/api/v1/problems"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
        mockMvc.perform(get("/api/v1/problems/two-sum")).andExpect(status().isNotFound)
    }

    @Test
    fun `언어로 거르면 제한 없는 문제도 함께 나온다`() {
        /*
          **이 필터의 핵심이다** (#618). 허용 목록이 비어 있으면 전부 허용이고(#419),
          목록의 대부분이 그렇다. 명시적으로 허용한 문제만 걸면 **거의 아무것도 안 나오고**,
          그런데도 화면은 그럴듯해 보인다.
        */
        givenRuntimeProblems()

        /*
          setUp 의 공개 문제 둘(two-sum·join-basics)은 **허용 목록이 비어 있다** — 분야가
          SQL 이어도 유형은 stdio 라 파이썬으로 푼다. 여기에 python-only 와
          python-3-13-only 가 더해져 넷이다. 아희 전용과 진짜 SQL 문제는 빠진다.
        */
        mockMvc.perform(get("/api/v1/problems").param("language", "python"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(4))
    }

    @Test
    fun `그 언어로 못 푸는 문제는 나오지 않는다`() {
        givenRuntimeProblems()

        // 아희 전용 문제(#420)는 파이썬을 고른 사람에게 보이면 안 된다.
        val body = mockMvc.perform(get("/api/v1/problems").param("language", "python"))
            .andReturn().response.getContentAsString(Charsets.UTF_8)

        assert(!body.contains("aheui-only")) { "아희 전용이 파이썬 목록에 있다: $body" }
        assert(!body.contains("sql-only")) { "SQL 문제가 파이썬 목록에 있다: $body" }
    }

    @Test
    fun `런타임까지 좁히면 그 버전만 허용한 문제가 걸린다`() {
        // 문제가 특정 버전만 허용할 수 있다 (#419). 언어까지만 골라서는 그것을 못 가른다.
        givenRuntimeProblems()

        // 제한 없는 둘 + 3.12 를 허용한 하나. **3.13 만 허용한 문제는 빠진다.**
        mockMvc.perform(get("/api/v1/problems").param("runtime", "python:3.12"))
            .andExpect(jsonPath("$.totalElements").value(3))

        val body = mockMvc.perform(get("/api/v1/problems").param("runtime", "python:3.12"))
            .andReturn().response.getContentAsString(Charsets.UTF_8)
        assert(!body.contains("python-3-13-only")) { "3.13 만 허용한 문제가 3.12 목록에 있다: $body" }
    }

    @Test
    fun `SQL 은 제품까지 좁힐 수 있다`() {
        // postgres 와 mariadb 는 같은 "SQL" 이지만 문법이 다르다 (#454).
        givenRuntimeProblems()

        mockMvc.perform(get("/api/v1/problems").param("language", "sql"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].slug").value("sql-only"))
        // postgres 만 허용한 문제라 mariadb 를 고르면 빈 목록이다.
        mockMvc.perform(get("/api/v1/problems").param("runtime", "sql:mariadb11"))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `모르는 언어를 고르면 빈 목록이다`() {
        // **거른 적 없는 것처럼 전부 나오면 안 된다.** 오타를 낸 사람이 알 수 없다.
        givenRuntimeProblems()

        mockMvc.perform(get("/api/v1/problems").param("language", "파이선"))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    /** 허용 언어가 서로 다른 문제 셋을 더한다. `setUp` 의 셋에 얹는다. */
    private fun givenRuntimeProblems() {
        transactionTemplate.execute {
            problemRepository.saveAll(
                listOf(
                    newProblem("python-only", "파이썬 전용", ProblemCategory.ALGORITHM, Difficulty.SILVER_5, true)
                        .apply { replaceAllowedRuntimes(listOf("python:3.12", "python:3.13")) },
                    newProblem("python-3-13-only", "3.13 전용", ProblemCategory.ALGORITHM, Difficulty.SILVER_5, true)
                        .apply { replaceAllowedRuntimes(listOf("python:3.13")) },
                    newProblem("aheui-only", "아희 전용", ProblemCategory.ALGORITHM, Difficulty.SILVER_5, true)
                        .apply { replaceAllowedRuntimes(listOf("aheui:1.2")) },
                    /*
                      **분야가 SQL 인 것과 유형이 SQL 인 것은 다르다.** setUp 의
                      `join-basics` 는 분야만 SQL 이라 파이썬으로 푸는 stdio 문제다 —
                      그것을 SQL 문제로 착각하면 이 필터를 잘못 검사하게 된다.
                    */
                    newProblem("sql-only", "진짜 SQL", ProblemCategory.SQL, Difficulty.SILVER_5, true)
                        .apply {
                            problemKind = ProblemKind.JUDGE_SQL
                            replaceAllowedRuntimes(listOf("sql:postgres16"))
                        },
                ),
            )
        }
    }

    private fun newProblem(
        slug: String,
        title: String,
        category: ProblemCategory,
        difficulty: Difficulty,
        published: Boolean,
    ) = Problem(
        slug = slug,
        title = title,
        category = category,
        difficultyLevel = difficulty.level,
        description = "설명",
        published = published,
    )

    @Test
    fun `정렬 기준마다 순서가 달라진다`() {
        // 웹이 이 파라미터를 보내지 않아 목록이 늘 최신순이었다 (#132).
        mockMvc.perform(get("/api/v1/problems").param("sort", "DIFFICULTY"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].difficultyLevel").value(1))

        mockMvc.perform(get("/api/v1/problems").param("sort", "TITLE"))
            .andExpect(status().isOk)

        // 기본은 최신순이다 — 인자를 보내지 않던 화면이 그대로 돌아야 한다.
        val latest = mockMvc.perform(get("/api/v1/problems"))
            .andReturn().response.contentAsString
        val explicit = mockMvc.perform(get("/api/v1/problems").param("sort", "LATEST"))
            .andReturn().response.contentAsString
        assertEquals(latest, explicit)
    }

    @Test
    fun `알 수 없는 정렬 기준은 거부한다`() {
        // 조용히 최신순으로 넘어가면 사용자는 정렬이 안 먹는다고 느낀다.
        mockMvc.perform(get("/api/v1/problems").param("sort", "POPULAR"))
            .andExpect(status().isBadRequest)
    }
}
