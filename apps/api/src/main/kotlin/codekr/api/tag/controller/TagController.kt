package codekr.api.tag.controller

import codekr.api.config.security.PublicApi
import codekr.api.tag.dto.TagResponse
import codekr.api.tag.service.TagService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 태그 목록 (#232).
 *
 * 공개다 — 무엇으로 문제를 고를 수 있는지는 로그인 전에도 보여야 한다. 화면이 태그를
 * 하드코딩하지 않게 하려는 것이 이 API 의 목적이므로, 태그를 늘려도 화면은 그대로다.
 */
@RestController
@RequestMapping("/api/v1/tags")
class TagController(private val tagService: TagService) {

    @PublicApi
    @GetMapping
    fun list(): List<TagResponse> = tagService.list()
}
