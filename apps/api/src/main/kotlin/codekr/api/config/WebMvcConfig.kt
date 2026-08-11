package codekr.api.config

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 컨트롤러 메서드가 [AuthPrincipal] 파라미터를 선언하면 SecurityContext 에서 꺼내 주입한다.
 *
 * 파라미터가 **널 허용이면 비로그인도 허용**한다 (#61). 공개 화면인데 로그인 여부에 따라
 * 내용이 달라지는 경우가 있다 — 대회 상세는 누구나 보지만, 참가자에게만 문제가 보인다.
 *
 * 널 허용이 아닌데 인증이 없으면(설정 실수 등) 401 로 끊는다.
 */
@Configuration
class WebMvcConfig : HandlerMethodArgumentResolver, WebMvcConfigurer {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == AuthPrincipal::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AuthPrincipal? {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? AuthPrincipal
        if (principal != null) return principal
        // 널 허용 파라미터는 "로그인했으면 알려 달라" 는 뜻이다.
        if (parameter.isOptional) return null
        throw ApiException(ErrorCode.UNAUTHORIZED)
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(this)
    }
}
