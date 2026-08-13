package codekr.api.scaling.service

/**
 * 실행기 배포의 replica 를 읽고 바꾸는 경계.
 *
 * 클러스터 안에서만 의미가 있으므로 구현을 갈아 끼울 수 있게 인터페이스로 둔다 —
 * 로컬 개발에서는 "사용 불가" 구현이 주입된다.
 */
interface ExecutorScaleClient {

    val available: Boolean

    /** 사용할 수 없는 이유. [available] 이 true 면 null 이다. */
    val unavailableReason: String?

    /**
     * 보고 있는 네임스페이스 (#237).
     *
     * 화면에 보여야 한다 — "그 이름의 배포가 없다" 는 말은 어느 네임스페이스에서 없다는
     * 것인지 알아야 고칠 수 있다.
     */
    val namespace: String?

    /**
     * 현재 원하는 replica 수와 준비된 replica 수.
     *
     * [namespaceOverride] 가 있으면 그곳을 본다 (#390). 조정 대상이 여럿이 되면서
     * **대상마다 다른 네임스페이스에 있을 수 있다** — 실행기는 Pod Security 가 느슨한
     * 곳에 따로 놓이고, 채점기는 그렇지 않다.
     */
    fun read(deployment: String, namespaceOverride: String? = null): Pair<Int, Int>

    fun scale(deployment: String, replicas: Int, namespaceOverride: String? = null)
}
