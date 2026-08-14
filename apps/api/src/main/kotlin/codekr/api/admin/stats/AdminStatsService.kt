package codekr.api.admin.stats

import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * 어드민 대시보드가 보는 숫자 (#550).
 *
 * **고르는 기준은 "보고 나서 할 일이 달라지는가" 다.** 그래서 다섯이다 —
 * 제출 추세(쓰이고 있는가), 가입 추세(들어오고 있는가), 판정 분포(우리가 아픈가),
 * 언어 비중(무엇을 지원할 값이 있는가), 유형별 문제 수(무엇을 갖고 있는가).
 */
@Service
class AdminStatsService(
    private val repository: AdminStatsRepository,
    private val clock: Clock,
) {

    fun overview(days: Int): AdminStatsResponse {
        val today = LocalDate.ofInstant(clock.instant(), SEOUL)
        val from = today.minusDays((days - 1).toLong())
        // 분포는 **짧게 본다.** 30일치를 합치면 어제 생긴 고장이 옛 자료에 묻힌다.
        val recent = today.minusDays((RECENT_DAYS - 1).toLong())

        return AdminStatsResponse(
            days = days,
            recentDays = RECENT_DAYS,
            submissions = fill(repository.submissionsByDay(from), from, today),
            signups = fill(repository.signupsByDay(from), from, today),
            verdicts = repository.verdictShare(recent),
            runtimes = repository.runtimeShare(recent),
            problemKinds = repository.problemsByKind(),
        )
    }

    /**
     * 빈 날을 0 으로 채운다.
     *
     * **제출이 없는 날은 행이 아예 없다.** 그대로 그리면 선이 그 구간을 건너뛰어,
     * 아무도 안 낸 날이 **완만한 하락**처럼 보인다 — 없는 것과 적은 것은 다른 말이다.
     */
    private fun fill(rows: List<DayCount>, from: LocalDate, to: LocalDate): List<DayCount> {
        val known = rows.associateBy { it.day }
        return generateSequence(from) { day -> day.plusDays(1).takeIf { !it.isAfter(to) } }
            .map { known[it] ?: DayCount(it, 0, 0) }
            .toList()
    }

    private companion object {
        /** 날짜 경계는 사용자가 사는 곳을 따른다 (#117 의 활동 그래프와 같은 규칙). */
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        /** 분포를 보는 기간. 고장은 최근 것이 중요하다. */
        const val RECENT_DAYS = 7
    }
}

/** 대시보드 한 화면에 필요한 것 전부. **여러 번 부르지 않게 한 번에 준다.** */
data class AdminStatsResponse(
    val days: Int,
    val recentDays: Int,
    val submissions: List<DayCount>,
    val signups: List<DayCount>,
    val verdicts: List<NamedCount>,
    val runtimes: List<NamedCount>,
    val problemKinds: List<NamedCount>,
)
