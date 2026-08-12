package codekr.api.submission.repository

import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionKind
import codekr.api.submission.entity.SubmissionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface SubmissionRepository : JpaRepository<Submission, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): Submission?

    /** 대회 제출 간격 제한에 쓴다 (#62). 문제마다 따로 센다. */
    /** 대회 밖에서 같은 사람이 같은 문제에 마지막으로 낸 제출 (#189). */
    fun findFirstByUserIdAndProblemIdAndContestIdIsNullAndDeletedAtIsNullOrderByIdDesc(
        userId: Long,
        problemId: Long,
    ): Submission?

    fun findFirstByContestIdAndUserIdAndProblemIdAndDeletedAtIsNullOrderByIdDesc(
        contestId: Long,
        userId: Long,
        problemId: Long,
    ): Submission?

    // 사용자에게 보이는 목록은 항상 kind 로 걸러 검증 제출이 섞이지 않게 한다.
    fun findByUserIdAndKindAndDeletedAtIsNullOrderByIdDesc(
        userId: Long,
        kind: SubmissionKind,
        pageable: Pageable,
    ): Page<Submission>

    fun findByUserIdAndProblemIdAndKindAndDeletedAtIsNullOrderByIdDesc(
        userId: Long,
        problemId: Long,
        kind: SubmissionKind,
        pageable: Pageable,
    ): Page<Submission>

    /** 재채점 대상 (#107). 정답 검증 제출은 대상이 아니라 kind 로 거른다. */
    fun findByProblemIdAndKindAndDeletedAtIsNull(problemId: Long, kind: SubmissionKind): List<Submission>

    /** 재채점 대상 수 (#219). 누르기 전에 몇 건인지 보여주려고 센다 — 목록까지 읽을 이유가 없다. */
    fun countByProblemIdAndKindAndDeletedAtIsNull(problemId: Long, kind: SubmissionKind): Long

    /** 스위퍼가 오래 방치된 제출을 찾을 때 쓴다. */
    fun findByStatusInAndCreatedAtBefore(
        statuses: Collection<SubmissionStatus>,
        threshold: Instant,
    ): List<Submission>
}
