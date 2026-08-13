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

    fun validate(request: ProblemUpsertRequest) {

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
        if (request.published && request.problemKind != ProblemKind.JUDGE_SQL && request.testcases.isEmpty()) {
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
