package codekr.api.submission.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.entity.ProblemFile
import codekr.api.submission.dto.SubmitRequest

/**
 * 여러 파일로 낸 제출을 받아들인다 (#457).
 *
 * **화면이 아니라 서버가 규칙을 지킨다.** API 를 직접 부르는 길이 생겨도 같아야 하기
 * 때문이다. 여기서 보는 것은 셋이다.
 *
 * 1. 문제가 파일 목록을 갖고 있는가 — 없으면 지금까지처럼 소스 하나짜리 제출이다
 * 2. 고칠 수 있는 파일이 모두 왔는가, 모르는 이름이 섞이지 않았는가
 * 3. 고칠 수 없는 파일은 **제출에서 무시하고 문제의 것을 쓴다** — 실려 와도 믿지 않는다
 */
object SubmissionFiles {

    /**
     * 채점에 실을 파일들을 만든다. 파일 문제가 아니면 null 이다.
     *
     * @param declared 그 런타임의 파일 목록 (비어 있으면 파일 문제가 아니다)
     */
    fun resolve(declared: List<ProblemFile>, request: SubmitRequest): Map<String, String>? {
        if (declared.isEmpty()) {
            // 파일 목록이 없는 문제에 파일을 실어 보내면 거절한다 — 어디에도 쓰이지 않을
            // 자료를 조용히 버리면, 사용자는 자기가 쓴 것이 채점됐다고 믿는다.
            if (!request.files.isNullOrEmpty()) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "이 문제는 파일 하나로 풉니다.")
            }
            return null
        }

        val editable = declared.filter { it.editable }
        val submitted = request.files.orEmpty().associate { it.name to it.sourceCode }

        val unknown = submitted.keys - declared.map { it.name }.toSet()
        if (unknown.isNotEmpty()) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "이 문제에 없는 파일입니다: ${unknown.sorted().joinToString()}",
            )
        }
        val missing = editable.map { it.name } - submitted.keys
        if (missing.isNotEmpty()) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "빠진 파일이 있습니다: ${missing.sorted().joinToString()}",
            )
        }

        // 고칠 수 없는 파일은 **문제의 것으로 덮는다.** 제출이 실어 보냈더라도 그렇다.
        return declared.associate { file ->
            file.name to if (file.editable) submitted.getValue(file.name) else file.template
        }
    }

    /**
     * 진입점 파일의 내용. 여러 파일 제출도 `source_code` 를 채운다 (#457).
     *
     * 소스를 하나로 보는 기존 경로(제출 목록·상세·통계)가 그대로 돌게 하기 위함이고,
     * **진실은 파일 목록 쪽**이다. 어느 것이 진입점인지는 문제의 파일 목록 첫 줄이 정한다.
     */
    fun entrySource(declared: List<ProblemFile>, files: Map<String, String>): String =
        declared.firstOrNull()?.let { files[it.name] } ?: files.values.firstOrNull().orEmpty()
}
