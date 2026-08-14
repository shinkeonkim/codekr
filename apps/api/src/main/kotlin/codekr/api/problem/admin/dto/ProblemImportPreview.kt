package codekr.api.problem.admin.dto

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.entity.ProblemKind

/**
 * 묶음을 저장하기 전에 무엇이 들어올지 보여준다 (#537).
 *
 * #479 의 완료 조건에 **"저장 전에 무엇이 들어올지 보인다"** 가 있었는데, 만드는 경로
 * 하나뿐이라 그럴 자리가 없었다. 파일을 고르자마자 만들어 버리면 잘못 만든 묶음이
 * **문제 번호를 하나 먹고** 지워야 할 것으로 남는다 — 번호는 사용자에게 보이는 값이다 (#204).
 *
 * **아무것도 만들지 않는다.** 읽고 검사한 결과만 돌려준다.
 */
data class ProblemImportPreview(
    /** zip 으로 읽었는지 맨 JSON 으로 읽었는지. 올린 사람이 착각했는지 여기서 드러난다. */
    val source: BundleSource,
    val slug: String,
    val title: String,
    val category: ProblemCategory,
    val problemKind: ProblemKind,
    val difficulty: Difficulty?,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    val testcaseCount: Int,
    /**
     * 이 유형이 테스트케이스로 채점되는가 (#455, #561).
     *
     * **화면이 유형 이름을 나열하지 않게 한다.** SQL·Redis 은 테스트케이스가 0개인 것이
     * 정상인데, 그 목록을 화면이 따로 들면 유형이 늘 때 빠뜨린다 — 서버가 이미 같은
     * 이유로 `ProblemKind.needsTestcases` 를 두고 있다.
     */
    val needsTestcases: Boolean,
    /** 테스트케이스를 어디서 가져왔는지. 묶음 파일이 본문의 것을 이긴다는 규칙이 여기서 보인다. */
    val testcaseSource: TestcaseSource,
    val templateCount: Int,
    /**
     * 묶음이 `published: true` 라고 적었는가.
     *
     * 적었더라도 **초안으로 덮는다.** 그 사실을 화면이 말해 줘야 올린 사람이
     * "왜 공개가 안 됐지" 를 겪지 않는다.
     */
    val publishedInBundle: Boolean,
    /**
     * 검증에 걸린 것들.
     *
     * **던지지 않고 모아서 돌려준다.** 미리보기는 무엇이 잘못됐는지 보는 자리라,
     * 첫 번째에서 멈추면 고치고 다시 올리기를 반복하게 된다.
     * 하나라도 있으면 저장은 실패한다.
     */
    val violations: List<String>,
) {
    enum class BundleSource { ZIP, JSON }

    /** `FILES` = 묶음 안의 `testcases` 폴더, `INLINE` = `problem.json` 의 `testcases` 배열. */
    enum class TestcaseSource { FILES, INLINE, NONE }
}
