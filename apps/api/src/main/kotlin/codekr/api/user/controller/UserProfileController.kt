package codekr.api.user.controller

import codekr.api.user.dto.UserProfileResponse
import codekr.api.user.service.UserProfileService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserProfileController(private val userProfileService: UserProfileService) {

    /**
     * 회원 프로필.
     *
     * **로그인이 필요하다.** 전체 제출 목록(#34)이 이미 로그인 사용자에게만 열려 있으므로,
     * 그것을 사람 기준으로 묶은 이 화면만 더 열어 둘 이유가 없다. 공개 범위를 바꾼다면
     * 두 곳을 함께 바꿔야 한다 — 한쪽만 열면 우회로가 된다.
     */
    @GetMapping("/{nickname}")
    fun findProfile(@PathVariable nickname: String): UserProfileResponse =
        userProfileService.findByNickname(nickname)
}
