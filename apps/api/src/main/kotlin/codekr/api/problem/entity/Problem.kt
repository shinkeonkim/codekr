package codekr.api.problem.entity

import codekr.api.common.entity.SoftDeletableEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "problems")
class Problem(

    // 유일성은 살아 있는 행에만 적용되는 부분 유니크 인덱스가 담당한다 (V1__init.sql).
    @Column(nullable = false)
    var slug: String,

    @Column(nullable = false)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var category: ProblemCategory,

    /**
     * 난이도를 정수로 저장하는 이유: 티어 범위 검색과 정렬을 인덱스로 처리하기 위함이다.
     * 애플리케이션에서는 [difficulty] 로만 다루고 이 값을 직접 쓰지 않는다.
     */
    @Column(name = "difficulty_level", nullable = false)
    var difficultyLevel: Int,

    @Column(nullable = false, columnDefinition = "text")
    var description: String,

    @Column(name = "input_description", columnDefinition = "text")
    var inputDescription: String? = null,

    @Column(name = "output_description", columnDefinition = "text")
    var outputDescription: String? = null,

    @Column(name = "time_limit_ms", nullable = false)
    var timeLimitMs: Int = 2000,

    @Column(name = "memory_limit_mb", nullable = false)
    var memoryLimitMb: Int = 256,

    @Column(nullable = false)
    var published: Boolean = false,

    @Column(name = "created_by")
    var createdBy: Long? = null,

) : SoftDeletableEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    /** 저장 컬럼이 아니라 [difficultyLevel] 을 읽고 쓰는 창구다 (뒷받침 필드가 없어 JPA 가 매핑하지 않는다). */
    var difficulty: Difficulty
        get() = Difficulty.ofLevel(difficultyLevel)
        set(value) {
            difficultyLevel = value.level
        }

    // 소프트 삭제된 자식까지 담고 있는 원본 컬렉션. 밖으로는 살아 있는 것만 노출한다.
    @OneToMany(mappedBy = "problem", cascade = [CascadeType.ALL])
    @OrderBy("seq ASC")
    private val allTestcases: MutableList<ProblemTestcase> = mutableListOf()

    @OneToMany(mappedBy = "problem", cascade = [CascadeType.ALL])
    private val allTemplates: MutableList<ProblemTemplate> = mutableListOf()

    val testcases: List<ProblemTestcase> get() = allTestcases.filter { !it.isDeleted }

    val examples: List<ProblemTestcase>
        get() = testcases.filter { it.visibility == TestcaseVisibility.PUBLIC }

    val templates: List<ProblemTemplate> get() = allTemplates.filter { !it.isDeleted }

    /** 런타임별 초기 코드. 문제에 지정된 값이 없으면 호출자가 런타임 기본 템플릿을 쓴다. */
    fun templateOf(runtimeId: String): String? =
        templates.firstOrNull { it.runtimeId == runtimeId }?.sourceCode

    /**
     * 테스트케이스와 템플릿은 부분 수정하지 않고 항상 통째로 교체한다.
     *
     * 교체는 "기존 것을 소프트 삭제 → 새로 추가" 두 단계다. `(problem_id, seq)` 부분 유니크 인덱스가
     * 살아 있는 행에만 걸려 있으므로, 삭제가 DB 에 먼저 반영되어야 같은 순번을 다시 넣을 수 있다.
     * 그래서 두 단계를 분리해 호출자가 사이에 flush 할 수 있게 한다.
     */
    fun softDeleteTestcases() = allTestcases.forEach { it.softDelete() }

    fun addTestcases(testcases: List<ProblemTestcase>) {
        testcases.forEach { it.assignTo(this) }
        allTestcases.addAll(testcases)
    }

    fun softDeleteTemplates() = allTemplates.forEach { it.softDelete() }

    fun addTemplates(templates: List<ProblemTemplate>) {
        templates.forEach { it.assignTo(this) }
        allTemplates.addAll(templates)
    }

    /** 문제를 지우면 딸린 테스트케이스와 템플릿도 함께 지워진 것으로 본다. */
    fun delete(now: Instant = Instant.now()) {
        softDelete(now)
        allTestcases.forEach { it.softDelete(now) }
        allTemplates.forEach { it.softDelete(now) }
    }
}
