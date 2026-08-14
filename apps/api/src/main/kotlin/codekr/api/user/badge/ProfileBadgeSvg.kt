package codekr.api.user.badge

/**
 * 프로필 배지 그림 (#475).
 *
 * **밖에서 이 사이트를 가리키는 길 중 유일하게 사용자가 스스로 거는 것**이다. README·
 * 블로그에 한 줄이면 박히고, 그것을 보는 사람은 우리가 광고로 닿을 수 없는 자리에 있다.
 *
 * ## 밖의 것을 하나도 참조하지 않는다
 *
 * GitHub 은 이미지를 자기 프록시(Camo)로 받아서 보여 준다. 그 안에서는 **웹폰트도,
 * 다른 이미지도, 바깥 CSS 도 따라오지 않는다.** 그래서 글꼴은 기기에 있는 것으로만
 * 적고, 그림은 도형과 글자로만 그린다.
 *
 * ## 어두운 배경은 질의 문자열로 고른다
 *
 * `prefers-color-scheme` 을 쓰고 싶지만, `<img>` 로 박힌 SVG 에서는 **보는 쪽의 설정이
 * 그림 안까지 닿지 않는다** — 프록시를 거치면 더 그렇다. GitHub README 가 라이트·다크를
 * 함께 쓰는 방식(`picture` + `media`)을 따를 수 있게 **주소로 테마를 고르게** 한다.
 */
object ProfileBadgeSvg {

    /** 배지에 그릴 값. 프로필이 이미 내려주는 것들이다 (#83) — 새로 계산하는 것이 없다. */
    data class Data(
        val handle: String,
        val tierName: String?,
        val score: Int,
        val solvedCount: Int,
        val streak: Int,
    )

    private const val WIDTH = 420
    private const val HEIGHT = 120

    /**
     * 없는 사람·탈퇴한 사람의 배지 (#140, #475).
     *
     * **404 를 주지 않는다.** README 에서 깨진 이미지로 보이는 것은 그 사람의 잘못처럼
     * 보이지 않고 **우리 고장으로** 보인다. 대신 아무 숫자도 없는 배지를 준다.
     *
     * 탈퇴한 사람과 없는 사람을 **같은 그림으로** 준다 — 다르게 주면 "이 handle 은
     * 있었다" 가 새어 나간다.
     */
    fun unknown(theme: BadgeTheme): String = render(theme) {
        """
        <text x="24" y="52" class="name">코드.kr</text>
        <text x="24" y="82" class="muted">없는 사용자입니다</text>
        """.trimIndent()
    }

    fun of(data: Data, theme: BadgeTheme): String = render(theme) {
        val stats = listOf(
            "푼 문제" to data.solvedCount.toString(),
            "점수" to data.score.toString(),
            "연속" to "${data.streak}일",
        )
        val cells = stats.mapIndexed { index, (label, value) ->
            val x = 24 + index * 132
            """
            <text x="$x" y="76" class="value">${escape(value)}</text>
            <text x="$x" y="98" class="label">${escape(label)}</text>
            """.trimIndent()
        }
        val tier = data.tierName?.let { """<text x="396" y="40" class="tier">${escape(it)}</text>""" }
        """
        <text x="24" y="40" class="name">${escape(data.handle)}</text>
        ${tier.orEmpty()}
        ${cells.joinToString("\n")}
        """.trimIndent()
    }

    private fun render(theme: BadgeTheme, body: () -> String): String =
        """
        <svg xmlns="http://www.w3.org/2000/svg" width="$WIDTH" height="$HEIGHT"
             viewBox="0 0 $WIDTH $HEIGHT" role="img" aria-label="코드.kr 프로필">
          <style>
            /* 기기에 있는 글꼴만 적는다 — 프록시 안에서는 웹폰트가 따라오지 않는다. */
            text { font-family: -apple-system, "Segoe UI", "Noto Sans KR", sans-serif; }
            .name { font-size: 20px; font-weight: 700; fill: ${theme.ink}; }
            .tier { font-size: 13px; font-weight: 600; fill: ${theme.accent}; text-anchor: end; }
            .value { font-size: 22px; font-weight: 700; fill: ${theme.ink}; }
            .label { font-size: 12px; fill: ${theme.muted}; }
            .muted { font-size: 14px; fill: ${theme.muted}; }
          </style>
          <rect x="0.5" y="0.5" width="${WIDTH - 1}" height="${HEIGHT - 1}" rx="12"
                fill="${theme.background}" stroke="${theme.border}"/>
          ${body()}
        </svg>
        """.trimIndent()

    /**
     * 배지에 들어가는 글자는 **사용자가 정한 handle** 이다.
     *
     * SVG 는 XML 이라 `<` 하나로 그림이 깨지고, `<script>` 를 심을 수 있는 자리이기도
     * 하다. handle 의 모양을 믿지 않고 여기서 막는다 — 규칙이 바뀌어도 이 자리는 남는다.
     */
    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

/** 배지 색. 밝은 쪽이 기본이다 — README 는 대개 밝은 배경에서 먼저 읽힌다. */
enum class BadgeTheme(
    val background: String,
    val border: String,
    val ink: String,
    val muted: String,
    val accent: String,
) {
    LIGHT("#ffffff", "#e2e8f0", "#0f172a", "#64748b", "#2563eb"),
    DARK("#0f172a", "#1e293b", "#f8fafc", "#94a3b8", "#60a5fa"),
    ;

    companion object {
        /** 모르는 값은 밝은 쪽이다 — 주소를 손으로 적다 틀렸을 때 깨지지 않게. */
        fun of(raw: String?): BadgeTheme =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LIGHT
    }
}
