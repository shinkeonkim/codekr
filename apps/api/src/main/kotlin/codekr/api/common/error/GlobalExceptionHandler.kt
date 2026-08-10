package codekr.api.common.error

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

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
