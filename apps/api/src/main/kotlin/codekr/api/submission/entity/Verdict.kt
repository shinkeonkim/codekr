package codekr.api.submission.entity

/**
 * 채점 판정.
 *
 * `label` 은 사용자에게 보여줄 한국어다 (#187). 화면과 알림이 각자 문구를 들고 있으면
 * 같은 판정이 두 곳에서 다르게 불린다.
 */
enum class Verdict(val label: String) {
    ACCEPTED("맞았습니다"),
    WRONG_ANSWER("틀렸습니다"),
    TIME_LIMIT_EXCEEDED("시간 초과"),
    MEMORY_LIMIT_EXCEEDED("메모리 초과"),
    RUNTIME_ERROR("런타임 오류"),
    COMPILE_ERROR("컴파일 오류"),
    OUTPUT_LIMIT_EXCEEDED("출력 초과"),
    SYSTEM_ERROR("시스템 오류"),
}
