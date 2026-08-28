package codekr.api.problem.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 정규식 문제의 스펙 (#653).
 *
 * **정답 패턴이 없다.** SQL·Redis 는 정답을 돌려 기대값을 만들지만, 정규식은
 * "이 문자열은 맞아야 하고 저것은 아니어야 한다" 가 곧 기대값이다 — 정답 패턴으로
 * 기대값을 만들면 **출제자가 실수한 패턴이 그대로 정답이 되어** 아무도 못 잡는다.
 */
@Entity
@Table(name = "problem_regex_specs")
class ProblemRegexSpec(
    @Id
    @Column(name = "problem_id")
    val problemId: Long,

    /** 한 줄에 하나. 첫 글자가 판정(`+`/`-`)이고 나머지가 문자열이다. */
    @Column(nullable = false)
    var cases: String,

    /**
     * 전체가 맞아야 하는가.
     *
     * **문제가 정하고 지문에 적어야 한다** — `match` 와 `search` 는 다른 문제다.
     * 기본을 전체 일치로 두는 이유: 부분 일치는 `.` 하나로도 통과하는 문제가 많다.
     */
    @Column(name = "full_match", nullable = false)
    var fullMatch: Boolean = true,

    @Column(name = "ignore_case", nullable = false)
    var ignoreCase: Boolean = false,
)
