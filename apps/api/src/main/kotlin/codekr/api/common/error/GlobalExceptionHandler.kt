package codekr.api.common.error

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(e.errorCode.status).body(ErrorResponse.of(e.errorCode, e.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldErrors = e.bindingResult.fieldErrors.map {
            FieldErrorResponse(it.field, it.defaultMessage ?: "올바르지 않은 값입니다.")
        }
        val code = ErrorCode.VALIDATION_ERROR
        return ResponseEntity.status(code.status).body(ErrorResponse(code.name, code.message, fieldErrors))
    }

    /**
     * 질의 인자의 타입이 맞지 않을 때 (#132).
     *
     * 값이 잘못된 것은 **요청 잘못**이다. 500 을 주면 사용자에게는 우리 잘못으로 보이고,
     * 로그에는 오류가 쌓이는데 고칠 것이 없다.
     *
     * enum 인자에서 특히 자주 난다 — `?sort=POPULAR` 처럼 없는 값을 보내는 경우다.
     * 어떤 값이 되는지 알려 준다. 모르면 고칠 수 없다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        val allowed = e.requiredType?.enumConstants?.joinToString(", ")
        val message = if (allowed != null) {
            "${e.name} 은 다음 중 하나여야 합니다: $allowed"
        } else {
            "${e.name} 의 값이 올바르지 않습니다."
        }
        val code = ErrorCode.VALIDATION_ERROR
        return ResponseEntity.status(code.status)
            .body(ErrorResponse(code.name, message, listOf(FieldErrorResponse(e.name, message))))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ErrorCode.FORBIDDEN.status).body(ErrorResponse.of(ErrorCode.FORBIDDEN))

    /**
     * 서블릿 컨테이너가 먼저 끊은 업로드 (#115).
     *
     * 우리 상한보다 크게 잡아 두었으므로 여기까지 오는 것은 **한참 큰 파일**이다.
     * 그래도 500 이 아니라 413 이어야 한다 — 서버가 고장 난 것이 아니라 파일이 큰 것이다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadTooLarge(e: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ErrorCode.IMAGE_TOO_LARGE.status).body(ErrorResponse.of(ErrorCode.IMAGE_TOO_LARGE))

    /**
     * 요청이 잘못된 경우들 (#324).
     *
     * 넷 다 **500 으로 나가고 있었다.** 마지막 `Exception` 처리기가 전부 받아서
     * "처리되지 않은 예외" 로 기록했기 때문이다. 뜻이 바뀌는 것이 문제다 —
     * 500 은 "우리 잘못" 이고, 오타로 부른 경로 하나가 경보를 울리면 **진짜 장애가
     * 그 사이에 묻힌다.** #132 가 잘못된 질의 인자에서 같은 판단을 했다.
     *
     * **여기서는 `log.error` 를 쓰지 않는다.** 우리가 고칠 것이 없는 일이라,
     * 오류 로그에 쌓이면 그 로그가 신호가 아니라 잡음이 된다.
     *
     * 없는 경로를 404 로 알리는 것이 정보 노출인지 — **인증 필터가 먼저 걸리므로**
     * 비로그인에게는 401 이 먼저 간다. 확인했다:
     * `GET /api/v1/nope` → 401, 토큰이 있을 때만 404 가 보인다.
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.status)
            .body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))

    /** 405 는 **어떤 메서드가 되는지**까지 알려 준다 — 모르면 고칠 수 없다. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
        val allowed = e.supportedMethods?.takeIf { it.isNotEmpty() }?.joinToString(", ")
        val message = if (allowed != null) {
            "${e.method} 는 쓸 수 없습니다. 이 경로에서는 $allowed 를 씁니다."
        } else {
            ErrorCode.METHOD_NOT_ALLOWED.message
        }
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.status)
            .header("Allow", allowed ?: "")
            .body(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED, message))
    }

    /**
     * 본문이 JSON 이 아니거나 중간에서 끊겼을 때.
     *
     * **파서의 말을 그대로 넘기지 않는다** — 줄·열 번호와 클래스 이름이 딸려 오는데,
     * 그것은 우리 내부 구조다.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ErrorCode.MALFORMED_REQUEST.status)
            .body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST))

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaType(e: HttpMediaTypeNotSupportedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.status)
            .body(ErrorResponse.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE))

    /** 빠뜨린 질의 인자. 어느 것인지 필드 오류로 실어 화면이 그 칸을 짚을 수 있게 한다. */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(e: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> {
        val message = "${e.parameterName} 이(가) 필요합니다."
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status)
            .body(ErrorResponse(ErrorCode.VALIDATION_ERROR.name, message, listOf(FieldErrorResponse(e.parameterName, message))))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        // 예상치 못한 예외의 내부 정보는 응답에 노출하지 않고 로그에만 남긴다.
        log.error("처리되지 않은 예외", e)
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status).body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR))
    }
}
