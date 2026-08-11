package codekr.api.contest.scoreboard

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 참가자용 순위표 캐시 (#62, #63).
 *
 * **대회 중 가장 많이 호출되는 화면이다.** 수백 명이 10초마다 부르면 그 자체가 부하다.
 *
 * 캐시 수명은 화면의 폴링 주기와 맞춘다 — 더 짧으면 캐시가 무의미하고, 더 길면
 * 사용자가 갱신을 눌러도 같은 화면을 본다.
 *
 * 어드민 화면은 캐시하지 않는다. 운영 판단에 쓰는 숫자가 몇 초 늦으면 안 된다.
 */
@Component
class ScoreboardCache {

    private val entries = ConcurrentHashMap<Long, Entry>()

    fun get(contestId: Long, compute: () -> ScoreboardResponse): ScoreboardResponse {
        val now = Instant.now()
        entries[contestId]?.takeIf { it.expiresAt > now }?.let { return it.value }

        val computed = compute()
        entries[contestId] = Entry(computed, now.plus(TTL))
        return computed
    }

    /** 재채점·문제 제외처럼 순위가 즉시 바뀌어야 하는 일이 생기면 버린다. */
    fun evict(contestId: Long) {
        entries.remove(contestId)
    }

    /** 전부 버린다. 시험이 서로에게 영향을 주지 않게 하는 데 쓴다. */
    fun clear() {
        entries.clear()
    }

    private data class Entry(val value: ScoreboardResponse, val expiresAt: Instant)

    private companion object {
        val TTL: Duration = Duration.ofSeconds(10)
    }
}
