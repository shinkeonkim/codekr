package codekr.api.collection.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.collection.dto.CollectionDetailResponse
import codekr.api.collection.dto.CollectionSummaryResponse
import codekr.api.collection.dto.CollectionUpsertRequest
import codekr.api.collection.service.ProblemCollectionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
 * 문제집 (#87).
 *
 * **공개 목록이 없다.** 내 것을 보거나, 링크로 남의 것을 연다 — 1차에서 공개 문제집을
 * 두지 않기로 한 결과다 (스팸·중복·방치를 정리할 도구가 아직 없다).
 */
@RestController
@RequestMapping("/api/v1/collections")
class ProblemCollectionController(private val collectionService: ProblemCollectionService) {

    @GetMapping("/me")
    fun findMine(principal: AuthPrincipal): List<CollectionSummaryResponse> =
        collectionService.findMine(principal.userId)

    @GetMapping("/{id}")
    fun findOne(
        @PathVariable id: Long,
        principal: AuthPrincipal?,
    ): CollectionDetailResponse = collectionService.findOne(id, principal?.userId)

    /** 링크 공유. 번호로 훑을 수 없게 추측 불가능한 토큰을 쓴다. */
    @GetMapping("/shared/{shareToken}")
    fun findShared(
        @PathVariable shareToken: String,
        principal: AuthPrincipal?,
    ): CollectionDetailResponse = collectionService.findByToken(shareToken, principal?.userId)

    @PostMapping
    fun create(
        @RequestBody @Valid request: CollectionUpsertRequest,
        principal: AuthPrincipal,
    ): ResponseEntity<CollectionDetailResponse> = ResponseEntity
        .status(HttpStatus.CREATED)
        .body(collectionService.create(principal.userId, request))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: CollectionUpsertRequest,
        principal: AuthPrincipal,
    ): CollectionDetailResponse = collectionService.update(id, principal.userId, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long, principal: AuthPrincipal) =
        collectionService.delete(id, principal.userId)
}
