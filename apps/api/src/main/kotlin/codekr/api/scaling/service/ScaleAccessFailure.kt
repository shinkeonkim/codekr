package codekr.api.scaling.service

/**
 * 조정 대상 배포에 닿지 못한 이유 (#237, #431).
 *
 * **셋의 대응이 완전히 다르다.** 권한이 없으면 차트의 Role 을, 대상이 없으면 이름과
 * 네임스페이스를, 닿지 못하면 네트워크·인증서를 봐야 한다. 예외를 통째로 삼키던 때는
 * 화면에도 로그에도 "읽지 못했습니다" 한 줄뿐이라 어느 쪽인지 알 수 없었다.
 */
enum class ScaleAccessFailure(
    /**
     * 어드민 화면에 보일 문구.
     *
     * **내부 정보를 그대로 뿌리지 않는다.** 상태 코드·응답 본문·인증서 오류는 로그에만
     * 남긴다 — 어드민 화면이라도 그것이 그대로 보이면 알아야 할 사람보다 넓게 퍼진다.
     */
    val template: String,
) {
    /**
     * 401·403. 토큰은 닿았는데 이 대상에 대한 권한이 없다.
     *
     * **이름이 틀려도 여기로 온다.** 차트의 Role 이 `resourceNames: ["codekr-executor"]` 로
     * 대상을 좁혀 두었기 때문에, 목록에 없는 이름을 물으면 API 서버가 404 가 아니라 403 을
     * 돌려준다 — 홈랩 클러스터에서 확인했다 (#237).
     */
    FORBIDDEN("%s 배포를 볼 권한이 없거나 이름이 Role 에 허용된 것과 다릅니다. 차트의 Role·RoleBinding 과 배포 이름을 확인하세요."),

    /** 404. 네임스페이스가 실제와 다를 때 주로 나온다. */
    NOT_FOUND("그 이름의 %s 배포를 찾지 못했습니다. 이름과 네임스페이스를 확인하세요."),

    /** 연결 자체가 되지 않았다 — 네트워크 정책, 인증서 신뢰, DNS. */
    UNREACHABLE("쿠버네티스 API 에 닿지 못했습니다. 네트워크 정책과 인증서를 확인하세요."),

    /** 위 어디에도 들지 않는다. 로그를 봐야 한다. */
    UNKNOWN("%s 배포 상태를 읽지 못했습니다. 서버 로그를 확인하세요."),
    ;

    /**
     * 어느 대상이 실패했는지 담아서 돌려준다 (#431).
     *
     * 전에는 문구가 전부 "실행기 배포" 로 시작했다. 대상이 하나였을 때는 맞았지만
     * 셋이 된 뒤로는(#390) **채점기가 실패해도 "실행기" 라고 말했고**, 화면에 같은
     * 문장이 두 번 떠서 읽는 사람이 실행기 문제라고 믿었다 — 진단을 늦추는 문구다.
     */
    fun messageFor(label: String): String = template.format(label)
}

/** 조정 대상 배포 접근 실패. [failure] 는 화면용, [cause] 와 [detail] 은 로그용이다. */
class ScaleAccessException(
    val failure: ScaleAccessFailure,
    val detail: String,
    cause: Throwable? = null,
) : RuntimeException(detail, cause)
