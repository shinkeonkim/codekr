package codekr.api.common.error

import org.springframework.http.HttpStatus

/** 클라이언트에 그대로 노출되는 오류 코드. 메시지는 사용자에게 보여줄 한국어 문구다. */
enum class ErrorCode(val status: HttpStatus, val message: String) {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않거나 만료되었습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 slug 입니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "문제를 찾을 수 없습니다."),
    CONTEST_NOT_FOUND(HttpStatus.NOT_FOUND, "대회를 찾을 수 없습니다."),
    COLLECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "문제집을 찾을 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "파일 저장소를 쓸 수 없습니다."),
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "제출을 찾을 수 없습니다."),
    RUNTIME_NOT_FOUND(HttpStatus.NOT_FOUND, "지원하지 않는 실행 환경입니다."),

    SOURCE_CODE_TOO_LARGE(HttpStatus.BAD_REQUEST, "소스 코드가 너무 큽니다."),
    TESTCASE_REQUIRED(HttpStatus.BAD_REQUEST, "테스트케이스가 최소 1개 필요합니다."),
    SOLUTION_REQUIRED(HttpStatus.BAD_REQUEST, "검증하려면 정답 코드를 먼저 등록해야 합니다."),

    EXECUTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "코드 실행에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    SCALING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이 환경에서는 실행기 수를 조정할 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
}
