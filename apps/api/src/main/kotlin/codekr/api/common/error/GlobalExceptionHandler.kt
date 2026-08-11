package codekr.api.common.error

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

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
            "${'$'}{e.name} 은 다음 중 하나여야 합니다: ${'$'}allowed"
        } else {
            "${'$'}{e.name} 의 값이 올바르지 않습니다."
        }
        val code = ErrorCode.VALIDATION_ERROR
        return ResponseEntity.status(code.status)
            .body(ErrorResponse(code.name, message, listOf(FieldErrorResponse(e.name, message))))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ErrorCode.FORBIDDEN.status).body(ErrorResponse.of(ErrorCode.FORBIDDEN))

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        // 예상치 못한 예외의 내부 정보는 응답에 노출하지 않고 로그에만 남긴다.
        log.error("처리되지 않은 예외", e)
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status).body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR))
    }
}
