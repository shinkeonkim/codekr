package codekr.api.submission.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * 여러 파일로 낸 제출을 한 칸에 담는다 (#457).
 *
 * **표로 나누지 않은 이유**는 이관 파일에 적어 두었다 — 제출은 많고 대부분 파일 하나인데
 * 표를 만들면 그 경우에도 조인이 하나 는다. 파일은 언제나 통째로 읽고 통째로 쓴다.
 */
@Converter
class SourceFilesConverter : AttributeConverter<Map<String, String>?, String?> {

    override fun convertToDatabaseColumn(attribute: Map<String, String>?): String? =
        attribute?.takeIf { it.isNotEmpty() }?.let(mapper::writeValueAsString)

    override fun convertToEntityAttribute(dbData: String?): Map<String, String>? =
        dbData?.takeIf { it.isNotBlank() }?.let {
            @Suppress("UNCHECKED_CAST")
            mapper.readValue(it, Map::class.java) as Map<String, String>
        }

    private companion object {
        val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    }
}
