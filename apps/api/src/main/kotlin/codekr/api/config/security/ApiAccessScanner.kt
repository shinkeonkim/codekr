package codekr.api.config.security

import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.reflect.Method

/**
 * 컨트롤러의 선언을 읽어 인가 규칙을 만든다 (#198).
 *
 * **빈을 만들지 않고 클래스만 훑는다.** 필터 체인은 기동 아주 이른 시점에 만들어지는데,
 * 그때 컨트롤러 빈을 꺼내면 서비스·리포지토리가 줄줄이 딸려 오고 순환이 생길 수 있다.
 * 여기서 필요한 것은 애노테이션뿐이라 클래스 스캔으로 충분하다.
 *
 * **선언이 없는 핸들러가 있으면 기동을 멈춘다.** 손으로 적던 목록(#71)이 하던 일 —
 * "이건 누가 쓰는가" 를 한 번은 답하게 만드는 것 — 을 그대로 잇는다. 기본값으로 막고
 * 넘어가면 아무도 다시 안 본다.
 */
object ApiAccessScanner {

    private const val BASE_PACKAGE = "codekr.api"

    private val MAPPINGS = listOf(
        GetMapping::class.java to "GET",
        PostMapping::class.java to "POST",
        PutMapping::class.java to "PUT",
        PatchMapping::class.java to "PATCH",
        DeleteMapping::class.java to "DELETE",
    )

    fun scan(basePackage: String = BASE_PACKAGE): List<ApiRule> {
        val provider = ClassPathScanningCandidateComponentProvider(false).apply {
            addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
        }

        val rules = mutableListOf<ApiRule>()
        val undeclared = mutableListOf<String>()

        for (candidate: BeanDefinition in provider.findCandidateComponents(basePackage)) {
            val type = Class.forName(candidate.beanClassName ?: continue)
            val prefixes = classPrefixes(type)

            for (method in type.declaredMethods) {
                val (httpMethod, paths) = handlerMapping(method) ?: continue
                val level = levelOf(method, type)
                if (level == null) {
                    undeclared += "${type.simpleName}.${method.name}"
                    continue
                }
                for (prefix in prefixes) {
                    for (path in paths) rules += ApiRule(httpMethod, join(prefix, path), level)
                }
            }
        }

        require(undeclared.isEmpty()) {
            "접근 수준을 선언하지 않은 엔드포인트가 있습니다 — @PublicApi / @AuthenticatedApi / " +
                "@AdminApi 중 하나를 붙이세요 (#198): ${undeclared.sorted()}"
        }
        return rules.sortedWith(compareBy({ it.pattern }, { it.method }))
    }

    /** 메서드 선언이 클래스 선언을 이긴다 — 핸들러 하나만 공개인 경우가 흔하다. */
    private fun levelOf(method: Method, type: Class<*>): ApiAccessLevel? =
        declaredOn(method) ?: declaredOn(type)

    private fun declaredOn(element: java.lang.reflect.AnnotatedElement): ApiAccessLevel? {
        AnnotatedElementUtils.findMergedAnnotation(element, AdminApi::class.java)
            ?.let { return ApiAccessLevel.Role(it.role) }
        if (AnnotatedElementUtils.hasAnnotation(element, PublicApi::class.java)) return ApiAccessLevel.Public
        if (AnnotatedElementUtils.hasAnnotation(element, AuthenticatedApi::class.java)) {
            return ApiAccessLevel.Authenticated
        }
        return null
    }

    private fun classPrefixes(type: Class<*>): List<String> {
        val mapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping::class.java)
        val paths = mapping?.let { if (it.value.isNotEmpty()) it.value else it.path } ?: emptyArray()
        return if (paths.isEmpty()) listOf("") else paths.toList()
    }

    private fun handlerMapping(method: Method): Pair<String, List<String>>? {
        for ((annotation, httpMethod) in MAPPINGS) {
            val merged = AnnotatedElementUtils.findMergedAnnotation(method, annotation) ?: continue
            val paths = pathsOf(merged)
            return httpMethod to (if (paths.isEmpty()) listOf("") else paths)
        }
        return null
    }

    /** 애노테이션마다 `value`/`path` 가 따로라 리플렉션으로 읽는다. */
    private fun pathsOf(annotation: Annotation): List<String> {
        val value = annotation.annotationClass.java.getMethod("value").invoke(annotation) as Array<*>
        if (value.isNotEmpty()) return value.map { it as String }
        val path = annotation.annotationClass.java.getMethod("path").invoke(annotation) as Array<*>
        return path.map { it as String }
    }

    private fun join(prefix: String, path: String): String {
        val left = prefix.trimEnd('/')
        val right = path.trim('/')
        return if (right.isEmpty()) left.ifEmpty { "/" } else "$left/$right"
    }
}
