package codekr.api.board.service

import codekr.api.auth.security.AuthPrincipal
import codekr.api.board.dto.BoardOption
import codekr.api.board.dto.PostDetailResponse
import codekr.api.board.dto.PostSummaryResponse
import codekr.api.board.dto.PostUpsertRequest
import codekr.api.board.entity.Board
import codekr.api.board.entity.Post
import codekr.api.board.repository.PostRepository
import codekr.api.common.dto.PageResponse
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.contest.entity.ContestPhase
import codekr.api.contest.repository.ContestProblemRepository
import codekr.api.contest.repository.ContestRepository
import codekr.api.problem.entity.Problem
import codekr.api.problem.repository.ProblemRepository
import codekr.api.user.avatar.AvatarService
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.entity.WithdrawnUser
import codekr.api.user.repository.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 게시글 (#137). */
@Service
@Transactional(readOnly = true)
class PostService(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val problemRepository: ProblemRepository,
    private val contestProblemRepository: ContestProblemRepository,
    private val contestRepository: ContestRepository,
) {

    /** 한 문제에 붙은 질문 (#139). */
    fun findByProblem(problemId: Long, pageable: Pageable): PageResponse<PostSummaryResponse> {
        val page = postRepository.findByProblemIdAndDeletedAtIsNullOrderByIdDesc(problemId, pageable)
        val authors = authorsOf(page.content)
        val comments = commentCountsOf(page.content)
        val problems = problemsOf(page.content)
        return PageResponse.from(page.map { summaryOf(it, authors, comments[it.id] ?: 0, problems) })
    }

    fun countQuestions(problemId: Long): Long = postRepository.countByProblemIdAndDeletedAtIsNull(problemId)

    fun findPage(board: Board?, keyword: String?, pageable: Pageable): PageResponse<PostSummaryResponse> {
        val query = keyword?.trim().orEmpty()
        val page = when {
            board != null && query.isNotEmpty() ->
                postRepository.findByBoardAndTitleContainingIgnoreCaseAndDeletedAtIsNullOrderByIdDesc(
                    board,
                    query,
                    pageable,
                )
            board != null -> postRepository.findByBoardAndDeletedAtIsNullOrderByIdDesc(board, pageable)
            query.isNotEmpty() ->
                postRepository.findByTitleContainingIgnoreCaseAndDeletedAtIsNullOrderByIdDesc(query, pageable)
            else -> postRepository.findByDeletedAtIsNullOrderByIdDesc(pageable)
        }
        val authors = authorsOf(page.content)
        val comments = commentCountsOf(page.content)
        val problems = problemsOf(page.content)
        return PageResponse.from(page.map { summaryOf(it, authors, comments[it.id] ?: 0, problems) })
    }

    fun findDetail(id: Long, principal: AuthPrincipal?): PostDetailResponse {
        val post = require(id)
        val authors = authorsOf(listOf(post))

        return PostDetailResponse(
            summary = summaryOf(post, authors, commentCountsOf(listOf(post))[post.id] ?: 0, problemsOf(listOf(post))),
            // 문제 질문에는 정답 코드가 그대로 올라온다. 기본으로 가리고 펼칠 수 있게 한다.
            hideCode = post.problemId != null,
            body = post.body,
            // 어드민은 지울 수는 있어도 남의 글을 **고칠 수는 없다** —
            // 고치면 그 사람이 쓴 것으로 남는데, 실제로 쓴 사람은 다른 사람이다.
            editable = principal != null && post.authorId == principal.userId,
            deletable = principal != null && (post.authorId == principal.userId || canModerate(principal)),
        )
    }

    @Transactional
    fun create(principal: AuthPrincipal, request: PostUpsertRequest): PostDetailResponse {
        requireWritable(request.board, principal)
        request.problemId?.let { requireQuestionable(it) }
        val post = postRepository.save(
            Post(request.board, principal.userId, request.title, request.body, request.problemId),
        )
        return findDetail(post.id, principal)
    }

    @Transactional
    fun update(id: Long, principal: AuthPrincipal, request: PostUpsertRequest): PostDetailResponse {
        val post = require(id)
        if (post.authorId != principal.userId) throw ApiException(ErrorCode.FORBIDDEN)
        requireWritable(request.board, principal)

        post.board = request.board
        post.edit(request.title, request.body)
        return findDetail(id, principal)
    }

    @Transactional
    fun delete(id: Long, principal: AuthPrincipal) {
        val post = require(id)
        // 운영자는 글을 내릴 수 있어야 한다. 신고 도구가 없는 지금은 이것이 유일한 수단이다.
        if (post.authorId != principal.userId && !canModerate(principal)) {
            throw ApiException(ErrorCode.FORBIDDEN)
        }
        post.delete()
    }

    fun boards(principal: AuthPrincipal?): List<BoardOption> = Board.entries.map { board ->
        BoardOption(
            value = board,
            label = board.label,
            description = board.description,
            // 쓸 수 없는 게시판에 글쓰기 버튼을 보여주면 눌렀을 때 거부당한다.
            writable = principal != null && canWrite(board, principal),
        )
    }

    private fun requireWritable(board: Board, principal: AuthPrincipal) {
        if (!canWrite(board, principal)) throw ApiException(ErrorCode.FORBIDDEN)
    }

    private fun canWrite(board: Board, principal: AuthPrincipal): Boolean =
        board.writeRole == null || canModerate(principal)

    /** 게시판 관리자 이상. 위계는 `SecurityConfig` 가 갖고 있으므로 상위 역할도 함께 본다. */
    private fun canModerate(principal: AuthPrincipal): Boolean =
        principal.has(UserRole.BOARD_MANAGER) ||
            principal.has(UserRole.ADMIN) ||
            principal.has(UserRole.SUPERUSER)

    private fun require(id: Long): Post =
        postRepository.findByIdAndDeletedAtIsNull(id) ?: throw ApiException(ErrorCode.POST_NOT_FOUND)

    /** 목록의 작성자를 한 번에 읽는다. 글 수만큼 질의가 나가면 안 된다. */
    private fun authorsOf(posts: List<Post>): Map<Long, User> =
        userRepository.findAllById(posts.map { it.authorId }).associateBy { it.id }

    /** 목록의 댓글 수를 한 번에 센다. 글마다 세면 질의가 20번 더 나간다. */
    private fun commentCountsOf(posts: List<Post>): Map<Long, Long> {
        if (posts.isEmpty()) return emptyMap()
        return postRepository.countCommentsByPostIds(posts.map { it.id })
            .associate { it[0] as Long to it[1] as Long }
    }

    private fun summaryOf(
        post: Post,
        authors: Map<Long, User>,
        commentCount: Long,
        problems: Map<Long, Problem>,
    ): PostSummaryResponse {
        val author = authors[post.authorId]
        val problem = post.problemId?.let { problems[it] }
        return PostSummaryResponse.of(
            post,
            // 참조는 그대로 두고 **상태로 판단해** 그린다 (#140).
            WithdrawnUser.nicknameOf(author),
            AvatarService.urlOf(WithdrawnUser.avatarKeyOf(author)),
            commentCount,
            problem?.slug,
            problem?.title,
        )
    }

    /** 목록의 문제를 한 번에 읽는다. 글마다 읽으면 질의가 20번 더 나간다. */
    private fun problemsOf(posts: List<Post>): Map<Long, Problem> {
        val ids = posts.mapNotNull { it.problemId }
        if (ids.isEmpty()) return emptyMap()
        return problemRepository.findAllById(ids).associateBy { it.id }
    }

    /**
     * 이 문제에 지금 질문할 수 있는가 (#139).
     *
     * **대회가 진행 중인 문제에는 질문할 수 없다.** 질문이 곧 힌트가 되고,
     * 참가자마다 그것을 본 사람과 못 본 사람이 갈린다.
     */
    private fun requireQuestionable(problemId: Long) {
        problemRepository.findByIdAndDeletedAtIsNull(problemId)
            ?: throw ApiException(ErrorCode.PROBLEM_NOT_FOUND)

        val now = Instant.now()
        val running = contestProblemRepository.findByIdProblemId(problemId)
            .mapNotNull { contestRepository.findByIdAndDeletedAtIsNull(it.id.contestId) }
            .any { it.phaseAt(now) == ContestPhase.RUNNING }

        if (running) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "대회가 진행 중인 문제에는 질문할 수 없습니다.")
        }
    }
}
