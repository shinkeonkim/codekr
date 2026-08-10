package codekr.api.common.error

data class FieldErrorResponse(val field: String, val message: String)

data class ErrorResponse(
    val code: String,
    val message: String,
    val fieldErrors: List<FieldErrorResponse> = emptyList(),
) {
    companion object {
        fun of(errorCode: ErrorCode, message: String = errorCode.message) =
            ErrorResponse(errorCode.name, message)
    }
}
