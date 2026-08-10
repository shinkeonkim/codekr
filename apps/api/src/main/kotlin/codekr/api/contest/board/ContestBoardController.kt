package codekr.api.contest.board

import codekr.api.auth.security.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 대회 공지와 질의 (#147).
 *
 * 공지는 공개다 — 참가하지 않은 사람도 "무슨 일이 있었나" 를 볼 수 있어야 대회가
 * 끝난 뒤 기록으로 남는다. 질의는 **볼 수 있는 것만** 내려간다.
 */
@RestController
@RequestMapping("/api/v1/contests/{slug}")
class ContestBoardController(private val boardService: ContestBoardService) {

    @GetMapping("/notices")
    fun notices(@PathVariable slug: String): List<NoticeResponse> = boardService.notices(slug)

    @PostMapping("/notices")
    fun addNotice(
        @PathVariable slug: String,
        @RequestBody @Valid request: NoticeUpsertRequest,
        principal: AuthPrincipal,
    ): NoticeResponse = boardService.addNotice(slug, principal, request)

    @DeleteMapping("/notices/{noticeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNotice(
        @PathVariable slug: String,
        @PathVariable noticeId: Long,
        principal: AuthPrincipal,
    ) = boardService.deleteNotice(slug, noticeId, principal)

    @GetMapping("/questions")
    fun questions(@PathVariable slug: String, principal: AuthPrincipal?): List<QuestionResponse> =
        boardService.questions(slug, principal)

    @PostMapping("/questions")
    fun ask(
        @PathVariable slug: String,
        @RequestBody @Valid request: QuestionRequest,
        principal: AuthPrincipal,
    ): QuestionResponse = boardService.ask(slug, principal, request)

    @PutMapping("/questions/{questionId}/answer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun answer(
        @PathVariable slug: String,
        @PathVariable questionId: Long,
        @RequestBody @Valid request: AnswerRequest,
        principal: AuthPrincipal,
    ) = boardService.answer(slug, questionId, principal, request)
}
