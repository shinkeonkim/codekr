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
 * 인증되지 않은 요청이 여기까지 오면(설정 실수 등) 401 로 끊는다.
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
    ): AuthPrincipal = SecurityContextHolder.getContext().authentication?.principal as? AuthPrincipal
        ?: throw ApiException(ErrorCode.UNAUTHORIZED)

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(this)
    }
}
