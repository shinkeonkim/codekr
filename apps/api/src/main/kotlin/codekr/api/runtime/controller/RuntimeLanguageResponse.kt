package codekr.api.runtime.controller

import codekr.api.runtime.RuntimeDefinition

/**
 * 목록 필터가 고를 수 있는 언어 한 갈래 (#618).
 *
 * **런타임 라벨에서 언어 이름을 뽑을 수 없다.** `sql:postgres16` 의 라벨은
 * "PostgreSQL 16" 이라 그대로 쓰면 SQL 이라는 갈래가 사라지고, `cpp:17` 은 "C++17
 * (GCC 13)" 이라 버전이 섞인다. 그래서 갈래 이름만 따로 적는다 — 이것은 **화면에 보일
 * 글자**이지 동작을 정하는 값이 아니다.
 */
data class RuntimeLanguageResponse(
    /** `python` 처럼 버전 없는 값. 목록 API 의 `language` 인자가 이것이다. */
    val id: String,
    val label: String,
    /** 이 언어의 런타임들. 화면이 "버전" 칸을 이것으로 채운다. */
    val runtimes: List<RuntimeOption>,
) {
    data class RuntimeOption(val id: String, val label: String)

    companion object {
        /**
         * 갈래 이름. **여기 없으면 id 를 그대로 쓴다** — 새 언어가 들어와도 화면이
         * 비지 않는다. 다만 이름이 어색하면 눈에 띄므로 그때 여기에 적는다.
         */
        private val LABELS = mapOf(
            "python" to "Python",
            "javascript" to "JavaScript",
            "cpp" to "C++",
            "c" to "C",
            "java" to "Java",
            "kotlin" to "Kotlin",
            "go" to "Go",
            "rust" to "Rust",
            "ruby" to "Ruby",
            "csharp" to "C#",
            "bash" to "Bash",
            "sql" to "SQL",
            "redis" to "Redis",
            "mongodb" to "MongoDB",
            "aheui" to "아희",
            "umjunsik" to "엄랭",
            "interactive" to "인터랙티브",
        )

        /**
         * 런타임들을 언어로 묶는다.
         *
         * **순서는 런타임 목록의 순서를 따른다.** 그 순서가 곧 우리가 사람들에게 권하는
         * 순서이고(`runtimes.yaml`), 여기서 다시 정렬하면 두 화면이 다른 순서를 보인다.
         */
        fun from(runtimes: List<RuntimeDefinition>): List<RuntimeLanguageResponse> =
            runtimes
                .groupBy { it.id.substringBefore(':') }
                .map { (language, group) ->
                    RuntimeLanguageResponse(
                        id = language,
                        label = LABELS[language] ?: language,
                        runtimes = group.map { RuntimeOption(it.id, it.label) },
                    )
                }
    }
}
