package codekr.api.problem.admin.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.admin.dto.ProblemUpsertRequest
import codekr.api.problem.entity.OutputComparison
import codekr.api.problem.entity.ProblemKind
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
     * NoSQL 문제도 어느 제품인지 정한다 (#455).
     *
     * SQL 과 같은 이유다 — 시드와 정답이 제품의 명령으로 쓰여 있으므로, 다른 제품으로
     * 제출되면 **출제자의 시드가 먼저 깨진다.**
     */
    private fun validateNoSqlProduct(request: ProblemUpsertRequest) {
        if (request.problemKind != ProblemKind.JUDGE_NOSQL) return

        val products = request.allowedRuntimeIds.filter {
            runtimeRegistry.exists(it) && runtimeRegistry.require(it).problemKind == ProblemKind.JUDGE_NOSQL
        }
        if (products.size != 1) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "NoSQL 문제는 어느 제품으로 푸는지 하나만 골라야 합니다. " +
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

    /**
     * 함수형 문제에는 **하네스가 있어야 한다** (#421).
     *
     * 없으면 사용자 코드를 부를 것이 없어 아무도 못 푸는 문제가 된다. 그리고 하네스를
     * 쓴 언어가 곧 허용 목록이므로(#419), 하나도 없으면 **풀 수 있는 언어도 없다.**
     */
    private fun validateHarnesses(request: ProblemUpsertRequest) {
        if (request.problemKind == ProblemKind.JUDGE_FUNCTION && request.harnesses.isEmpty()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "함수 구현 문제에는 언어별 하네스가 필요합니다.")
        }
        if (request.problemKind != ProblemKind.JUDGE_FUNCTION && request.harnesses.isNotEmpty()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "함수 구현 문제가 아닌데 하네스가 실려 있습니다.")
        }
        // 하네스를 얹을 수 없는 언어를 고르면, 그 언어로 낸 제출은 실행기가 거절한다.
        val unsupported = request.harnesses
            .map { it.runtimeId }
            .filterNot { runtimeRegistry.exists(it) && runtimeRegistry.require(it).supportsFunctionHarness }
        if (unsupported.isNotEmpty()) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "이 언어는 함수 구현 문제를 지원하지 않습니다: ${unsupported.joinToString()}",
            )
        }
    }

    fun validate(request: ProblemUpsertRequest) {
        validateSqlDatabase(request)
        validateHarnesses(request)
        validateInteractor(request)
        validateNoSqlProduct(request)

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
        // NoSQL 도 같은 규칙이다 (#455). 스펙이 없으면 무엇을 정답으로 볼지가 없다.
        if (request.problemKind == ProblemKind.JUDGE_NOSQL && request.nosqlSpec == null) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "NoSQL 문제에는 정답 명령과 상태를 읽는 명령이 필요합니다.",
            )
        }
        if (request.problemKind != ProblemKind.JUDGE_NOSQL && request.nosqlSpec != null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "NoSQL 문제가 아닌데 NoSQL 스펙이 실려 있습니다.")
        }
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
}
