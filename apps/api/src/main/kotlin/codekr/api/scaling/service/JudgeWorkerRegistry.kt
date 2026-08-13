package codekr.api.scaling.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * 채점기 워커 수를 적어 두는 자리 (#390).
 *
 * **인터페이스로 두는 이유**: 조정 서비스가 Redis 키 문자열을 알 이유가 없고,
 * 그것을 알면 시험이 Redis 를 띄워야 한다.
 */
interface JudgeWorkerRegistry {
    fun read(lane: String): Int?
    fun write(lane: String, workers: Int)
}

/**
 * Redis 구현.
 *
 * Redis 를 쓰는 이유: 이미 큐로 쓰고 있고(ADR-0002), **파드가 여럿이어도 전부가 같은
 * 값을 본다.** 채점기마다 엔드포인트를 두면 모든 파드에 따로 보내야 한다.
 *
 * **키 문자열이 채점기와 갈라지면 조정이 조용히 안 먹는다.** 그쪽은
 * `libs/gocontract` 의 `JudgeConcurrencyKey` 가 만든다 — 같은 규칙을 양쪽에 적는다.
 */
@Component
class RedisJudgeWorkerRegistry(private val redis: StringRedisTemplate) : JudgeWorkerRegistry {

    override fun read(lane: String): Int? = redis.opsForValue().get(key(lane))?.toIntOrNull()

    override fun write(lane: String, workers: Int) {
        redis.opsForValue().set(key(lane), workers.toString())
    }

    private fun key(lane: String) = "codekr:judge:concurrency:$lane"
}
