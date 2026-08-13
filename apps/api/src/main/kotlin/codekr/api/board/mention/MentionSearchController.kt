package codekr.api.board.mention

import codekr.api.config.security.AuthenticatedApi
import codekr.api.user.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 멘션 자동완성 (#214).
 *
 * **로그인해야 부른다.** 닉네임은 이미 랭킹·게시판에 공개돼 있지만, 비로그인에게까지
 * 이름 검색을 열어 줄 이유는 없다 — 멘션은 로그인한 사람만 쓴다.
 *
 * 어드민 회원 검색(#223)과 나눈 이유: 그쪽은 이메일까지 준다. 여기서 필요한 것은
 * **이름과 id 뿐**이고, 둘을 한 경로로 두면 권한이 헷갈린다.
 */
@RestController
@RequestMapping("/api/v1/users/mention-candidates")
class MentionSearchController(private val userRepository: UserRepository) {

    @AuthenticatedApi
    @GetMapping
    fun search(@RequestParam q: String): List<MentionResponse> {
        val keyword = q.trim()
        // 한 글자로 이름 목록을 훑지 못하게 한다 (#223 과 같은 판단).
        if (keyword.length < MIN_KEYWORD) return emptyList()

        return userRepository
            .findByNicknameContainingIgnoreCaseAndWithdrawnAtIsNull(keyword, PageRequest.of(0, LIMIT))
            .map(Mentions::of)
    }

    private companion object {
        const val MIN_KEYWORD = 2
        const val LIMIT = 5
    }
}
