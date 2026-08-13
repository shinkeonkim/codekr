package codekr.api.contest.repository

import codekr.api.contest.entity.ContestRegistration
import codekr.api.contest.entity.ContestRegistrationId
import codekr.api.contest.entity.ContestRegistrationStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ContestRegistrationRepository : JpaRepository<ContestRegistration, ContestRegistrationId> {

    fun countByIdContestId(contestId: Long): Int

    /**
     * 참가자 수 (#466). **대기 중인 사람은 세지 않는다** — 아직 참가자가 아니다.
     *
     * 사전 스케일(#62)이 이 값을 본다. 신청만 한 사람 몫까지 미리 늘리면 오지 않을
     * 사람을 위해 노드를 잡는다.
     */
    fun countByIdContestIdAndStatus(contestId: Long, status: ContestRegistrationStatus): Int

    /** 승인 대기 목록. 어드민 화면이 본다. */
    fun findByIdContestIdAndStatusOrderByRegisteredAtAsc(
        contestId: Long,
        status: ContestRegistrationStatus,
    ): List<ContestRegistration>
}
