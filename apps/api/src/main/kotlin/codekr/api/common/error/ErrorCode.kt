package codekr.api.common.error

import org.springframework.http.HttpStatus

/** 클라이언트에 그대로 노출되는 오류 코드. 메시지는 사용자에게 보여줄 한국어 문구다. */
enum class ErrorCode(val status: HttpStatus, val message: String) {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않거나 만료되었습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    /**
     * 정지된 회원의 쓰기·제출 (#224).
     *
     * 403 이되 `FORBIDDEN` 과 코드를 나눈다 — 화면이 "권한이 없는 화면" 과 "지금
     * 막혀 있는 나" 를 다르게 안내해야 한다. 메시지에 **사유와 언제 풀리는지**가
     * 실려 온다.
     */
    SUSPENDED(HttpStatus.FORBIDDEN, "정지된 계정입니다."),

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 slug 입니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "문제를 찾을 수 없습니다."),
    CONTEST_NOT_FOUND(HttpStatus.NOT_FOUND, "대회를 찾을 수 없습니다."),
    COLLECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "문제집을 찾을 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "태그를 찾을 수 없습니다."),
    SUSPENSION_NOT_FOUND(HttpStatus.NOT_FOUND, "정지 기록을 찾을 수 없습니다."),

    /**
     * 꺼져 있는 기능 (#285).
     *
     * 403 이 아니라 404 다 — **켜져 있는지 여부까지 감춘다.** "권한이 없다" 는 그
     * 기능이 존재한다는 말이기도 하다.
     */
    FEATURE_DISABLED(HttpStatus.NOT_FOUND, "사용할 수 없는 기능입니다."),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "파일 저장소를 쓸 수 없습니다."),
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "제출을 찾을 수 없습니다."),
    RUNTIME_NOT_FOUND(HttpStatus.NOT_FOUND, "지원하지 않는 실행 환경입니다."),

    SOURCE_CODE_TOO_LARGE(HttpStatus.BAD_REQUEST, "소스 코드가 너무 큽니다."),
    // 413 이다. 무엇이 문제인지가 상태 코드에 드러나야 화면이 안내를 고를 수 있다 (#115).
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "이미지가 너무 큽니다."),
    // 429 다. 400 으로 두면 화면이 "고칠 수 있는 잘못"으로 다루는데, 여기서 할 일은
    // 기다리는 것뿐이다 (#189).
    SUBMISSION_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "제출이 너무 잦습니다."),
    /** 인증 메일 재발송 같은 것 (#233). 발송량이 곧 비용이고 평판이다. */
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다."),
    /**
     * 이메일을 확인하지 않은 계정의 글쓰기 (#233).
     *
     * 403 이되 코드를 나눈다 — 화면이 "권한 없음" 이 아니라 **"인증 메일을 다시 받기"**
     * 를 안내해야 한다.
     */
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 확인이 필요합니다."),
    TESTCASE_REQUIRED(HttpStatus.BAD_REQUEST, "테스트케이스가 최소 1개 필요합니다."),
    SOLUTION_REQUIRED(HttpStatus.BAD_REQUEST, "검증하려면 정답 코드를 먼저 등록해야 합니다."),

    EXECUTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "코드 실행에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    SCALING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이 환경에서는 실행기 수를 조정할 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
}
