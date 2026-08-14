package codekr.api.problem.vote

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 투표가 난이도에 반영되는 규칙 (#477).
 *
 * **값을 설정으로 뺀 이유**: 지금 이 사이트는 사용자도 문제도 적다. 문턱을 코드에 박고
 * 높게 잡으면 **기능이 켜져 있는데 아무도 못 쓰는 상태**가 되고, 낮게 박으면 나중에
 * 올릴 때 배포가 필요하다. solved.ac 의 문턱(Platinum V)을 그대로 가져오면 지금
 * 우리에게는 투표할 수 있는 사람이 0명일 수 있다.
 */
@ConfigurationProperties(prefix = "codekr.difficulty-vote")
data class DifficultyVoteProperties(
    /**
     * `UNRATED` 문제에 난이도를 붙이는 데 필요한 표 수.
     *
     * **처음엔 낮게 둔다.** 표가 안 모여 아무 문제에도 난이도가 안 붙으면, 이 기능은
     * 있으나 마나다 — 사람이 늘면 올린다.
     */
    val minVotes: Int = 3,
)
