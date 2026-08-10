package codekr.api.user.dto

import codekr.api.user.entity.UserRole
import jakarta.validation.constraints.NotEmpty

/**
 * 역할 부여/회수 (#103).
 *
 * 가진 역할 전체를 넘긴다. 부분 수정(추가/삭제)이 아니라 통째로 교체하는 이유는,
 * 두 사람이 동시에 고칠 때 **무엇이 최종 상태인지가 명확**하기 때문이다.
 */
data class RoleChangeRequest(
    @field:NotEmpty(message = "역할을 하나 이상 지정해야 합니다.")
    val roles: Set<UserRole>,
)
