package codekr.api.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 어드민이 조정할 수 있는 워크로드 (#40, #390).
 *
 * **허용 목록이다.** 경로에 이름을 받아 아무 배포나 조정할 수 있게 열면, 어드민 화면이
 * 곧 클러스터 전체를 만지는 도구가 된다. 여기 적힌 것만 조정된다 —
 * 일반화의 편의와 안전을 둘 다 가지는 방법이다.
 *
 * 전에는 실행기 하나만 가리켰다(`executor-scaling`). 그런데 채점 파이프라인의
 * 워크로드는 셋이고, **채점기가 큐를 못 빼면 실행기를 늘려도 소용없다** —
 * 막히는 곳이 다르면 늘릴 것도 다르다.
 */
@ConfigurationProperties(prefix = "codekr.scaling")
data class ScalingProperties(
    val targets: Map<String, ScalingTarget> = emptyMap(),
) {
    fun target(key: String): ScalingTarget? = targets[key]
}

/**
 * 조정 대상 하나.
 *
 * **최소·최대가 대상마다 따로다.** 채점기와 실행기는 적정 범위가 다르다 — 실행기는
 * 샌드박스를 띄우느라 무겁고, 채점기는 큐를 빼는 일이라 가볍다.
 */
data class ScalingTarget(
    val label: String = "",
    val deployment: String = "",
    /**
     * 그 워크로드가 있는 네임스페이스. 비우면 api 자신의 것을 쓴다.
     *
     * 실행기는 런타임 소켓을 마운트하느라 Pod Security 가 느슨한 별도 네임스페이스에
     * 놓일 수 있다 (차트의 `executor.namespace`).
     */
    val namespace: String = "",
    val minReplicas: Int = 1,
    val maxReplicas: Int = 20,
    /**
     * 채점 차선 (#62). 있으면 **워커 수도 조정할 수 있다.**
     *
     * 파드 수와 워커 수는 듣는 곳이 다르다 — 워커는 대기 시간에, 파드는 처리량과
     * 격리에 듣는다. 실행기에는 차선이 없으므로 파드 수만 조정된다.
     */
    val lane: String = "",
) {
    val adjustsWorkers: Boolean get() = lane.isNotBlank()
}
