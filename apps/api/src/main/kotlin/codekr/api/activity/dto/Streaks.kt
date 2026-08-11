package codekr.api.activity.dto

/**
 * 스트릭 (#117).
 *
 * 프로필과 활동 그래프가 **같은 계산에서** 받아 간다. 각자 계산하면 언젠가 어긋나고,
 * 그때 사용자는 어느 쪽을 믿어야 할지 알 수 없다.
 */
data class Streaks(val current: Int, val longest: Int)
