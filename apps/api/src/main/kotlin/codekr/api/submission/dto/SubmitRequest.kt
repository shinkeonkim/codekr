package codekr.api.submission.dto

import codekr.api.submission.entity.SubmissionVisibility
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SubmitRequest(
    @field:NotBlank(message = "실행 환경은 필수입니다.")
    val runtimeId: String,

    /**
     * 파일 하나짜리 제출의 소스. **파일이 여럿인 문제에서는 비어 있을 수 있다** (#457).
     *
     * `@NotBlank` 를 뗀 이유: 파일 문제에서는 `files` 가 소스이고, 여기에 무엇을 넣어야
     * 하는지 화면이 알 수 없다. 대신 서버가 **둘 중 하나는 있어야 한다**를 본다.
     */
    val sourceCode: String = "",

    /** 여러 파일로 내는 문제의 제출 (#457). 파일 하나짜리 문제에서는 비운다. */
    val files: List<SubmitFile>? = null,

    /**
     * 소스 코드 공개 범위. **지정하지 않으면 사용자 기본값을 쓴다** (#104).
     *
     * null 을 기본값으로 둔 이유: PRIVATE 을 기본값으로 두면 "명시적으로 비공개를 골랐다"
     * 와 "아무것도 안 골랐다" 를 서버가 구분할 수 없다. 그러면 사용자 기본값을 적용할
     * 수 없다.
     */
    val visibility: SubmissionVisibility? = null,
)

/** 제출한 파일 하나 (#457). */
data class SubmitFile(
    @field:NotBlank(message = "파일 이름은 비어 있을 수 없습니다.")
    @field:Size(max = 60)
    val name: String,
    val sourceCode: String = "",
)
