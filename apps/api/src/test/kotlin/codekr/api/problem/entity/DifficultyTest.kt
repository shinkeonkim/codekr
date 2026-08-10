package codekr.api.problem.entity

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DifficultyTest {

    @Test
    fun `레벨은 브론즈 5부터 루비 1까지 1에서 30 이다`() {
        assertEquals(1, Difficulty.BRONZE_5.level)
        assertEquals(30, Difficulty.RUBY_1.level)
        assertEquals(30, Difficulty.entries.size)
    }

    @Test
    fun `레벨로 난이도를 되찾는다`() {
        Difficulty.entries.forEach { difficulty ->
            assertEquals(difficulty, Difficulty.ofLevel(difficulty.level))
        }
    }

    @Test
    fun `티어와 단계를 계산한다`() {
        assertEquals(DifficultyTier.BRONZE to 5, Difficulty.BRONZE_5.tier to Difficulty.BRONZE_5.step)
        assertEquals(DifficultyTier.GOLD to 4, Difficulty.GOLD_4.tier to Difficulty.GOLD_4.step)
        assertEquals(DifficultyTier.RUBY to 1, Difficulty.RUBY_1.tier to Difficulty.RUBY_1.step)
    }

    @Test
    fun `표기는 티어 이름과 단계로 만든다`() {
        assertEquals("골드 4", Difficulty.GOLD_4.label)
        assertEquals("다이아몬드 1", Difficulty.DIAMOND_1.label)
    }

    @Test
    fun `티어는 연속된 레벨 구간을 차지한다`() {
        assertEquals(1..5, DifficultyTier.BRONZE.levelRange)
        assertEquals(11..15, DifficultyTier.GOLD.levelRange)
        assertEquals(26..30, DifficultyTier.RUBY.levelRange)
        assertEquals(DifficultyTier.PLATINUM, DifficultyTier.ofLevel(18))
    }

    @Test
    fun `범위를 벗어난 레벨은 거부한다`() {
        assertFailsWith<IllegalArgumentException> { Difficulty.ofLevel(0) }
        assertFailsWith<IllegalArgumentException> { Difficulty.ofLevel(31) }
        assertFailsWith<IllegalArgumentException> { DifficultyTier.ofLevel(31) }
    }
}
