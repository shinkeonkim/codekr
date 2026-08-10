package codekr.api.contest.board

import org.springframework.data.jpa.repository.JpaRepository

interface ContestNoticeRepository : JpaRepository<ContestNotice, Long> {

    fun findByContestIdAndDeletedAtIsNullOrderByIdDesc(contestId: Long): List<ContestNotice>

    fun findByIdAndDeletedAtIsNull(id: Long): ContestNotice?
}

interface ContestQuestionRepository : JpaRepository<ContestQuestion, Long> {

    /**
     * 한 대회의 질의를 **한 번에** 읽는다.
     *
     * 볼 수 있는지는 애플리케이션이 거른다 — 질의 수가 많지 않고, 조건이 세 갈래라
     * (운영자·질문자·공개 답변) 질의로 표현하면 읽기 어려워진다.
     */
    fun findByContestIdOrderByIdDesc(contestId: Long): List<ContestQuestion>
}
