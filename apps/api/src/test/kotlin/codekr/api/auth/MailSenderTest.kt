package codekr.api.auth

import codekr.api.auth.email.MailProperties
import codekr.api.auth.email.MailSender
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 발송 실패 로그에 자격증명이 섞이지 않는다 (#355).
 *
 * **로그는 사람이 많이 보는 곳이고, 한번 찍힌 것은 되돌릴 수 없다.** 제공자가 인증
 * 실패 응답에 무엇을 싣는지는 우리가 정하지 못하므로, 나가는 자리에서 지운다.
 */
class MailSenderTest {

    private val properties = MailProperties(
        host = "smtp.example.com",
        port = 465,
        ssl = true,
        username = "contact@example.com",
        password = "app-password-1234",
    )

    @Test
    fun `앱 비밀번호가 로그에 남지 않는다`() {
        // 제공자가 서버 응답을 그대로 실어 보내는 경우다.
        val e = RuntimeException("535 5.7.8 Authentication failed for contact@example.com / app-password-1234")

        val line = MailSender(properties).scrub(e)

        assertFalse(line.contains("app-password-1234"), "비밀번호가 남았습니다: $line")
        assertFalse(line.contains("contact@example.com"), "계정 이름이 남았습니다: $line")
        assertTrue(line.contains("***"))
    }

    @Test
    fun `무엇이 실패했는지는 남는다`() {
        // 다 지워 버리면 로그가 쓸모없어진다. 원인은 알아볼 수 있어야 한다.
        val line = MailSender(properties).scrub(java.net.SocketTimeoutException("Read timed out"))

        assertEquals("SocketTimeoutException: Read timed out", line)
    }

    @Test
    fun `설정이 비어 있어도 지우기가 깨지지 않는다`() {
        // 빈 문자열을 그대로 replace 하면 글자 사이마다 *** 가 끼어든다.
        val line = MailSender(MailProperties()).scrub(RuntimeException("연결 실패"))

        assertEquals("RuntimeException: 연결 실패", line)
    }

    @Test
    fun `465 는 붙자마자 TLS 로 간다`() {
        /*
          **이 한 줄이 이 이슈의 전부다** (#355). STARTTLS 만 켜고 465 로 가면 평문
          EHLO 를 보내고 서버는 핸드셰이크를 기다려, 타임아웃까지 매달렸다가 실패한다.
          그리고 발송은 예외를 삼키므로 **가입은 성공하고 메일만 조용히 안 간다.**
        */
        val smtp = MailSender(properties).smtpProperties

        assertEquals("true", smtp["mail.smtp.ssl.enable"])
        // 둘은 다른 절차다. 함께 켜면 안 된다.
        assertEquals(null, smtp["mail.smtp.starttls.enable"])
    }

    @Test
    fun `587 은 STARTTLS 그대로다`() {
        val smtp = MailSender(properties.copy(port = 587, ssl = false)).smtpProperties

        assertEquals("true", smtp["mail.smtp.starttls.enable"])
        assertEquals(null, smtp["mail.smtp.ssl.enable"])
    }

    @Test
    fun `설정이 없으면 보내지 않는다`() {
        // 로컬에서 메일 서버 없이 전체가 돌아야 한다 (#115 와 같은 규칙).
        assertFalse(MailSender(MailProperties()).enabled)
        assertTrue(MailSender(properties).enabled)
    }
}
