package codekr.api.problem.admin.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.admin.dto.ProblemUpsertRequest
import codekr.api.problem.entity.OutputComparison
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.entity.QuizAnswerType
import codekr.api.runtime.RuntimeRegistry
import org.springframework.stereotype.Component

/**
 * 문제 저장 요청이 성립하는지 본다 (#59, #60).
 *
 * **저장 서비스에서 떼어 냈다.** 규칙이 늘어나는 자리라 한 파일 안에 두면 저장 흐름이
 * 규칙 목록에 묻힌다 — 지금 읽어야 할 것은 "무엇을 저장하는가" 인데 화면의 절반이
 * "무엇을 막는가" 였다.
 */
@Component
class ProblemUpsertValidator(private val runtimeRegistry: RuntimeRegistry) {

    private companion object {
        /** 기동을 빼고 쿼리에 남겨 둘 최소 시간 (#454). */
        const val SQL_QUERY_BUDGET_MS = 1000

        /** 보기가 하나뿐이면 고를 것이 없다 (#650). */
        const val MIN_CHOICES = 2
    }

    /**
     * 함수형 문제의 하네스 (#446).
     *
     * **하네스가 없으면 아무도 풀 수 없는 문제가 된다** — 허용 언어가 하네스로 정해지기
     * 때문이다(#419 와 같은 자리). 그래서 공개하려면 하나는 있어야 한다.
     */
    private fun validateHarnesses(request: ProblemUpsertRequest) {
        /*
            **하네스를 쓰는 유형이 둘이다** (#421, #651).

            함수 구현은 사용자가 함수를 쓰고 하네스가 그것을 부르며, 고치는 문제는
            사용자가 망가진 코드를 고치고 하네스가 **숨긴 시험**을 돌린다. 실어 보내는
            것도 허용 언어를 정하는 방식도 같아서 검증이 하나다.

            **하네스는 어드민에게만 간다** — 공개 상세 DTO 에 그 자리가 없다. 고치는
            문제에서 그것이 곧 "숨긴 시험" 의 근거다.
        */
        val kind = request.problemKind
        if (kind != ProblemKind.JUDGE_FUNCTION && kind != ProblemKind.JUDGE_PATCH) {
            if (request.harnesses.isNotEmpty()) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "하네스를 쓰는 유형이 아닌데 하네스가 실려 있습니다.")
            }
            return
        }
        val label = if (kind == ProblemKind.JUDGE_FUNCTION) "함수 구현" else "고치는"
        if (request.published && request.harnesses.isEmpty()) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "$label 문제는 하네스를 최소 하나 써야 합니다. 하네스가 있는 언어로만 풀 수 있습니다.",
            )
        }
        request.harnesses.keys.forEach { runtimeId ->
            val runtime = runtimeRegistry.require(runtimeId)
            if (!runtime.supportsFunctionHarness) {
                throw ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "이 언어로는 $label 문제를 낼 수 없습니다: ${runtime.label}",
                )
            }
        }
        // 허용 목록은 하네스가 정한다 — 두 곳이 같은 것을 정하면 어긋난다.
        if (request.allowedRuntimeIds.isNotEmpty()) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "$label 문제는 허용 언어를 따로 고르지 않습니다. 하네스를 쓴 언어가 곧 허용 언어입니다.",
            )
        }
        /*
            **고치는 문제에는 고칠 것이 있어야 한다** (#651).

            시작 코드가 없으면 사용자는 빈 파일을 받고, 그것은 "고치기" 가 아니라
            "처음부터 쓰기" 다 — 문제가 묻는 것이 조용히 바뀐다.
        */
        if (kind == ProblemKind.JUDGE_PATCH && request.published && request.files.none { it.editable }) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "고치는 문제에는 고칠 수 있는 파일이 하나 이상 필요합니다.",
            )
        }
    }

    /**
     * 스페셜 저지 (#452).
     *
     * **채점 코드 없이 `CHECKER` 를 고르면 아무도 못 푸는 문제가 된다** — 견줄 기대값도
     * 없고 물어볼 코드도 없어서 모든 제출이 SYSTEM_ERROR 로 끝난다.
     */
    private fun validateChecker(request: ProblemUpsertRequest) {
        val hasChecker = !request.checkerSource.isNullOrBlank()
        if (request.outputComparison == OutputComparison.CHECKER && !hasChecker) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "채점 코드로 판정하려면 채점 코드를 써야 합니다.",
            )
        }
        if (request.outputComparison != OutputComparison.CHECKER && hasChecker) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "채점 코드는 '채점 코드로 판정' 일 때만 쓸 수 있습니다.",
            )
        }
        // SQL 은 결과 집합을 견준다 (#60) — 출력 비교 방식 자체가 다른 자리다.
        if (request.outputComparison == OutputComparison.CHECKER &&
            request.problemKind == ProblemKind.JUDGE_SQL
        ) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "SQL 문제에는 채점 코드를 쓸 수 없습니다.")
        }
    }

    /**
     * SQL 문제는 **어느 DB 인지 정해야 한다** (#454).
     *
     * SQL 런타임이 둘이 된 순간, "비워 두면 전부 허용"(#419)은 SQL 문제에서 뜻이 달라졌다 —
     * PostgreSQL 문법으로 쓴 스키마·정답 쿼리가 MariaDB 제출에서도 돌게 된다. 그러면
     * **출제자의 스키마가 먼저 깨져** 제출자는 자기 잘못이 아닌 SYSTEM_ERROR 를 받는다.
     *
     * 그래서 **문제 하나에 DB 하나**다. 같은 질문을 두 DB 로 내고 싶으면 문제를 둘 만든다 —
     * 스키마도 정답도 지문도 어차피 갈라지기 때문이다.
     */
    private fun validateSqlDatabase(request: ProblemUpsertRequest) {
        if (request.problemKind != ProblemKind.JUDGE_SQL) return

        val databases = request.allowedRuntimeIds.filter {
            runtimeRegistry.exists(it) && runtimeRegistry.require(it).problemKind == ProblemKind.JUDGE_SQL
        }
        if (databases.size != 1) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "SQL 문제는 어느 데이터베이스로 푸는지 하나만 골라야 합니다. " +
                    "고른 것: ${databases.ifEmpty { listOf("없음") }.joinToString()}",
            )
        }

        /*
          **기동 시간도 문제의 시간 제한 안에서 흐른다.** 제한은 컨테이너 전체에 걸리기
          때문이다. MariaDB 는 뜨는 데만 3초 넘게 쓰므로, 2초 제한을 준 문제는 어떤 쿼리를
          내도 시간 초과가 된다 — 출제자는 그 이유를 짐작할 방법이 없다.
        */
        val startupMs = runtimeRegistry.require(databases.single()).startupMs
        if (startupMs > 0 && request.timeLimitMs < startupMs + SQL_QUERY_BUDGET_MS) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "이 데이터베이스는 뜨는 데만 ${startupMs}ms 를 씁니다. " +
                    "시간 제한을 ${startupMs + SQL_QUERY_BUDGET_MS}ms 이상으로 두세요.",
            )
        }
    }

    /**
     * Redis 문제도 어느 제품인지 정한다 (#455).
     *
     * SQL 과 같은 이유다 — 시드와 정답이 제품의 명령으로 쓰여 있으므로, 다른 제품으로
     * 제출되면 **출제자의 시드가 먼저 깨진다.**
     */
    private fun validateMongoProduct(request: ProblemUpsertRequest) {
        if (request.problemKind != ProblemKind.JUDGE_MONGODB) return

        val products = request.allowedRuntimeIds.filter {
            runtimeRegistry.exists(it) && runtimeRegistry.require(it).problemKind == ProblemKind.JUDGE_MONGODB
        }
        if (products.size != 1) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "MongoDB 문제는 어느 제품으로 푸는지 하나만 골라야 합니다. " +
                    "고른 것: ${products.ifEmpty { listOf("없음") }.joinToString()}",
            )
        }
        /*
          **기동 시간을 시간 제한에 넣어 준다** (#454 가 SQL 에서 낸 규칙).

          mongod 는 Redis 보다 훨씬 느리게 뜬다(실측 수 초). 그것을 빼고 제한을 잡으면
          제출이 아무리 빨라도 시간 초과가 나고, 출제자는 자기 문제가 왜 안 되는지 모른다.
        */
        val startupMs = runtimeRegistry.require(products.single()).startupMs
        if (startupMs > 0 && request.timeLimitMs < startupMs + SQL_QUERY_BUDGET_MS) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "MongoDB 는 뜨는 데만 ${startupMs}ms 가 걸립니다. " +
                    "시간 제한을 ${startupMs + SQL_QUERY_BUDGET_MS}ms 이상으로 잡으십시오.",
            )
        }
    }

    private fun validateRedisProduct(request: ProblemUpsertRequest) {
        if (request.problemKind != ProblemKind.JUDGE_REDIS) return

        val products = request.allowedRuntimeIds.filter {
            runtimeRegistry.exists(it) && runtimeRegistry.require(it).problemKind == ProblemKind.JUDGE_REDIS
        }
        if (products.size != 1) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "Redis 문제는 어느 제품으로 푸는지 하나만 골라야 합니다. " +
                    "고른 것: ${products.ifEmpty { listOf("없음") }.joinToString()}",
            )
        }
        val startupMs = runtimeRegistry.require(products.single()).startupMs
        if (startupMs > 0 && request.timeLimitMs < startupMs + SQL_QUERY_BUDGET_MS) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "이 제품은 뜨는 데만 ${startupMs}ms 를 씁니다. " +
                    "시간 제한을 ${startupMs + SQL_QUERY_BUDGET_MS}ms 이상으로 두세요.",
            )
        }
    }

    /**
     * 인터랙티브 문제에는 **대화를 주관할 코드가 있어야 한다** (#474).
     *
     * 없으면 아무도 못 푸는 문제가 된다 — 제출이 무엇을 물어도 답할 것이 없다.
     * #452 가 채점 코드에서 한 판단과 같다.
     */
    private fun validateInteractor(request: ProblemUpsertRequest) {
        val hasInteractor = !request.interactorSource.isNullOrBlank()
        if (request.problemKind == ProblemKind.JUDGE_INTERACTIVE && !hasInteractor) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "인터랙티브 문제에는 채점 코드가 필요합니다.")
        }
        if (request.problemKind != ProblemKind.JUDGE_INTERACTIVE && hasInteractor) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "인터랙티브 문제가 아닌데 채점 코드가 실려 있습니다.")
        }
    }

    fun validate(request: ProblemUpsertRequest) {
        validateSqlDatabase(request)
        validateInteractor(request)
        validateRedisProduct(request)
        validateMongoProduct(request)

        // 채점기 구현도 스펙 테이블도 없는 유형으로는 문제를 만들 수 없다 (#59).
        // 허용하면 채점되지 않는 문제가 만들어지고, 그 사실은 누가 제출한 뒤에야 드러난다.
        if (!request.problemKind.ready) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "아직 지원하지 않는 문제 유형입니다: ${request.problemKind.label}",
            )
        }
        // 유형별 자료는 그 유형에만 실린다 (#60). 섞이면 어느 쪽이 진짜인지 알 수 없다.
        if (request.problemKind == ProblemKind.JUDGE_SQL && request.sqlSpec == null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "SQL 문제에는 스키마와 정답 쿼리가 필요합니다.")
        }
        if (request.problemKind != ProblemKind.JUDGE_SQL && request.sqlSpec != null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "SQL 문제가 아닌데 SQL 스펙이 실려 있습니다.")
        }
        validateHarnesses(request)
        // Redis 도 같은 규칙이다 (#455). 스펙이 없으면 무엇을 정답으로 볼지가 없다.
        if (request.problemKind == ProblemKind.JUDGE_REDIS && request.redisSpec == null) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "Redis 문제에는 정답 명령과 상태를 읽는 명령이 필요합니다.",
            )
        }
        if (request.problemKind != ProblemKind.JUDGE_REDIS && request.redisSpec != null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "Redis 문제가 아닌데 Redis 스펙이 실려 있습니다.")
        }
        // MongoDB 도 같은 규칙이다 (#527).
        if (request.problemKind == ProblemKind.JUDGE_MONGODB && request.mongoSpec == null) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "MongoDB 문제에는 정답 스크립트와 끝난 뒤를 읽는 스크립트가 필요합니다.",
            )
        }
        if (request.problemKind != ProblemKind.JUDGE_MONGODB && request.mongoSpec != null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "MongoDB 문제가 아닌데 MongoDB 스펙이 실려 있습니다.")
        }
        validateQuiz(request)
        validateRegex(request)
        validateGit(request)
        /*
          쓰기를 열었는데 상태를 읽는 쿼리가 없으면 (#453) 채점은 **조용히** 결과 집합
          비교로 돌아간다. `UPDATE` 는 결과 집합이 비어 있으니 아무나 통과한다 —
          출제자는 자기 문제가 무엇을 재는지 모르는 채로 문제를 연다.
        */
        request.sqlSpec?.let { spec ->
            if (spec.allowWrite && spec.verifySql.isNullOrBlank()) {
                throw ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "쓰기를 여는 SQL 문제에는 끝난 뒤의 상태를 읽는 쿼리가 필요합니다.",
                )
            }
        }
        // 채점할 대상이 없는 문제는 공개해도 아무 의미가 없다.
        // SQL 문제의 채점 대상은 테스트케이스가 아니라 정답 쿼리다 (#60).
        if (request.published && request.problemKind.needsTestcases && request.testcases.isEmpty()) {
            throw ApiException(ErrorCode.TESTCASE_REQUIRED)
        }

        validateChecker(request)

        if (request.testcases.groupingBy { it.seq }.eachCount().any { it.value > 1 }) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "테스트케이스 순번이 중복되었습니다.")
        }
        if (request.templates.groupingBy { it.runtimeId }.eachCount().any { it.value > 1 }) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "같은 실행 환경의 초기 코드가 중복되었습니다.")
        }
        request.templates.firstOrNull { !runtimeRegistry.exists(it.runtimeId) }?.let {
            throw ApiException(ErrorCode.RUNTIME_NOT_FOUND, "지원하지 않는 실행 환경입니다: ${it.runtimeId}")
        }
        request.solution?.let { runtimeRegistry.require(it.runtimeId) }
    }

    /**
     * Git 의 규칙 (#654).
     *
     * **확인 명령이 커밋 해시를 그대로 찍으면 경고한다.** 하네스가 신원·시각을 고정해
     * 해시가 재현되기는 하지만, **메시지 한 글자만 달라도 해시가 달라져** 같은 결과에
     * 이른 다른 풀이가 틀린 답이 된다. 막지는 않는다 — 해시를 묻는 문제도 있을 수 있다.
     */
    private fun validateGit(request: ProblemUpsertRequest) {
        val spec = request.gitSpec
        if (request.problemKind != ProblemKind.JUDGE_GIT) {
            if (spec != null) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "Git 문제가 아닌데 Git 스펙이 실려 있습니다.")
            }
            return
        }
        if (spec == null) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "Git 문제에는 정답 명령과 끝난 뒤를 읽는 명령이 필요합니다.",
            )
        }
        /*
            **명령 파일에는 git 명령만 담긴다.** 하네스도 같은 규칙으로 막지만,
            거기서 막히면 사용자는 **출제자가 넣은 시드가 실패한 것**을 자기 잘못으로 본다.
            등록에서 먼저 잡는다.
        */
        for ((label, commands) in listOf("시드" to spec.seedCommands, "정답" to spec.answerCommands)) {
            commands.orEmpty().lines()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("git ") }
                ?.let {
                    throw ApiException(ErrorCode.VALIDATION_ERROR, "$label 명령은 git 으로 시작해야 합니다: $it")
                }
        }
    }

    /**
     * 정규식의 규칙 (#653).
     *
     * **맞으면 안 되는 문자열이 없으면 문제가 아니다.** `.*` 가 통과하기 때문이다 —
     * 그리고 그것은 오류를 내지 않으므로 출제자는 자기 문제가 아무것도 묻지 않는다는
     * 것을 모른다. #605 에서 Redis 문제 하나가 **틀린 답도 통과**했던 것과 같은 종류다.
     */
    private fun validateRegex(request: ProblemUpsertRequest) {
        val spec = request.regexSpec
        if (request.problemKind != ProblemKind.JUDGE_REGEX) {
            if (spec != null) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "정규식 문제가 아닌데 정규식 스펙이 실려 있습니다.")
            }
            return
        }
        if (spec == null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "정규식 문제에는 확인할 문자열이 필요합니다.")
        }

        val lines = spec.cases.lines().map { it.trim('\r') }.filter { it.isNotBlank() }
        lines.firstOrNull { !it.startsWith("+") && !it.startsWith("-") }?.let {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "확인할 문자열은 + 또는 - 로 시작해야 합니다: $it",
            )
        }
        if (lines.none { it.startsWith("+") }) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "맞아야 하는 문자열이 하나 이상 필요합니다.")
        }
        if (lines.none { it.startsWith("-") }) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "맞으면 안 되는 문자열이 하나 이상 필요합니다. 없으면 `.*` 가 통과합니다.",
            )
        }
    }

    /**
     * 퀴즈의 규칙 (#650).
     *
     * **다른 유형과 다른 것이 하나 있다: 난이도를 두지 않는다.** 점수는 난이도에서
     * 나오므로(#195) 그것이 곧 "랭킹 합에 넣지 않는다" 가 된다 — 찍어서 맞는 문제가
     * 합에 들어가면 순위의 뜻이 옅어진다. 랭킹 계산에 손대지 않고 얻는 결론이다.
     */
    private fun validateQuiz(request: ProblemUpsertRequest) {
        val spec = request.quizSpec
        if (request.problemKind != ProblemKind.QUIZ) {
            if (spec != null) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "퀴즈 문제가 아닌데 퀴즈 스펙이 실려 있습니다.")
            }
            return
        }
        if (spec == null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "퀴즈 문제에는 보기 또는 받아 줄 답이 필요합니다.")
        }
        if (request.difficulty != null) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "퀴즈에는 난이도를 매기지 않습니다. 찍어서 맞는 문제가 랭킹 점수에 들어가면 순위의 뜻이 옅어집니다.",
            )
        }

        if (spec.answerType.usesChoices) {
            if (spec.answers.isNotEmpty()) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "객관식에는 단답 정답을 넣지 않습니다.")
            }
            if (spec.choices.size < MIN_CHOICES) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "보기는 ${MIN_CHOICES}개 이상이어야 합니다.")
            }
            val correct = spec.choices.count { it.correct }
            // **정답이 없으면 아무도 못 맞힌다.** 오류가 나지 않아 정답률 0%로만 보인다.
            if (correct == 0) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "정답인 보기를 하나 이상 골라야 합니다.")
            }
            // 하나만 고르는 문제인데 정답이 여럿이면 **맞힐 수 있는 답이 여럿**이 되고,
            // 채점은 "정확히 일치" 라서 그중 무엇을 골라도 틀린다.
            if (spec.answerType == QuizAnswerType.SINGLE && correct > 1) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "하나만 고르는 문제에는 정답이 하나여야 합니다.")
            }
            return
        }

        if (spec.choices.isNotEmpty()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "단답에는 보기를 넣지 않습니다.")
        }
        if (spec.answers.isEmpty()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "받아 줄 답을 하나 이상 적어야 합니다.")
        }
    }

}
