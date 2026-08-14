package codekr.api.group.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class GroupRequest(
    @field:NotBlank(message = "이름을 입력해 주세요.")
    @field:Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    val name: String = "",
    @field:Size(max = 200, message = "소개는 200자 이하여야 합니다.")
    val description: String = "",
    /** **기본은 초대만이다.** 처음부터 공개면 스팸 가입이 온다 (기획서 5절). */
    val openJoin: Boolean = false,
)

/** 내가 든 그룹 한 줄. 목록에서 필요한 것만 담는다. */
data class GroupSummary(
    val id: Long,
    val name: String,
    val memberCount: Int,
    val owner: Boolean,
)

/**
 * 둘러보기에 나오는 공개 그룹 한 줄 (#554).
 *
 * [GroupSummary] 와 나눈 이유: 저기는 **내 그룹**이라 `owner` 가 뜻이 있는데, 여기는
 * 아직 들지 않은 그룹이라 그 자리에 필요한 것이 **이미 들었는가**이다.
 *
 * 소개를 함께 준다 — 이름만으로는 무엇을 하는 모임인지 모르고, 그러면 들어가 보고
 * 나오는 일이 반복된다.
 */
data class OpenGroupSummary(
    val id: Long,
    val name: String,
    val description: String,
    val memberCount: Int,
    val memberLimit: Int,
    /** 이미 든 그룹이면 들어가기 대신 그 사실을 보인다. */
    val member: Boolean,
)

data class GroupDetail(
    val id: Long,
    val name: String,
    val description: String,
    val openJoin: Boolean,
    val memberCount: Int,
    val memberLimit: Int,
    /** 내가 방장인가. 화면이 관리 단추를 보일지 정하는 값이다. */
    val owner: Boolean,
    val member: Boolean,
    /**
     * 초대 링크의 토큰. **방장에게만 간다.**
     *
     * 멤버 아무나 부를 수 있게 하면 방장이 인원을 통제할 길이 없다 — 그러면 초대
     * 링크가 사실상 공개 가입과 같아진다.
     */
    val inviteToken: String?,
    val members: List<GroupMemberView>,
)

data class GroupMemberView(
    val userId: Long,
    val nickname: String,
    val handle: String,
    val owner: Boolean,
    val joinedAt: Instant,
)

/** 초대 링크를 눌렀을 때 **가입 전에** 보여 줄 것 (#401). */
data class GroupInvitePreview(
    val id: Long,
    val name: String,
    val description: String,
    val memberCount: Int,
    /** 이미 들어 있으면 화면이 "가입" 대신 "열기" 를 보인다. */
    val member: Boolean,
)
