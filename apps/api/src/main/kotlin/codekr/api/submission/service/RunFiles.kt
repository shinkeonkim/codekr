package codekr.api.submission.service

import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.entity.ProblemSqlSpec

/**
 * 실행(`/run`)에 함께 실을 문제의 파일 (#525).
 *
 * **SQL 문제는 스키마 없이는 아무것도 할 수 없었다.** 실행은 소스 하나만 보냈고, 그래서
 * 어떤 쿼리를 써도 `relation "members" does not exist` 였다.
 *
 * **정답과 검사 쿼리는 절대 싣지 않는다.** 실행 결과는 그대로 사용자에게 돌아간다 —
 * 실었다면 하네스가 찍는 기대 결과(`--- codekr:expected`)가 화면에 그대로 보인다.
 * 채점(#60)은 이 함수를 쓰지 않고 `JudgeJobFactory` 가 따로 싣는다.
 *
 * **규칙을 한 곳에 두는 이유**: "무엇이 새면 안 되는가" 가 서비스 코드 안에 흩어져
 * 있으면, 다음에 유형이 하나 늘 때 그 판단을 다시 하게 된다.
 */
object RunFiles {

    fun of(kind: ProblemKind, sqlSpec: ProblemSqlSpec?): Map<String, String> {
        if (kind != ProblemKind.JUDGE_SQL || sqlSpec == null) return emptyMap()

        return buildMap {
            put("schema.sql", sqlSpec.schemaSql)
            // 쓰기를 여는 문제라면 실행에서도 열어야 한다 — 아니면 실행에서만 막힌다 (#453).
            if (sqlSpec.allowWrite) put("allow-write", "")
        }
    }
}
