package codekr.api.contest.repository

import codekr.api.contest.entity.ContestRegistration
import codekr.api.contest.entity.ContestRegistrationId
import org.springframework.data.jpa.repository.JpaRepository

interface ContestRegistrationRepository : JpaRepository<ContestRegistration, ContestRegistrationId> {

    fun countByIdContestId(contestId: Long): Int
}
