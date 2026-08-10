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

    /** 현재 원하는 replica 수와 준비된 replica 수. */
    fun read(deployment: String): Pair<Int, Int>

    fun scale(deployment: String, replicas: Int)
}
