package codekr.api.common.error

/** 도메인 규칙 위반을 표현하는 예외. 전역 핸들러가 [ErrorCode] 를 HTTP 응답으로 옮긴다. */
class ApiException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
) : RuntimeException(message)
