package codekr.api.ranking.dto

import java.time.Instant

/** 랭킹 한 줄 (#57, #85). 두 지표가 같은 줄을 공유해 화면이 지표를 바꿔도 값이 흔들리지 않는다. */
data class RankingEntry(
    val rank: Int,
    val nickname: String,
    val score: Int,
    val solvedCount: Int,
    /** 동점 처리에 쓰는 마지막 해결 시각. 화면에도 보여준다. */
    val lastSolvedAt: Instant?,
)
