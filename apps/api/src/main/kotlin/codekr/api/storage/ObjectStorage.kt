package codekr.api.storage

/**
 * 올린 파일을 두는 곳 (#115).
 *
 * 인터페이스로 둔 이유는 **저장 방식이 이 뒤에 갇히게** 하기 위함이다 —
 * 도메인 코드는 S3 를 쓰는지 알지 못한다.
 */
interface ObjectStorage {

    /** 쓸 수 있는 상태인가. 설정이 없으면 false 다. */
    val available: Boolean

    fun put(key: String, bytes: ByteArray, contentType: String)

    fun get(key: String): StoredObject?

    /** 없는 키를 지우는 것은 오류가 아니다 — 여러 번 불려도 같아야 한다. */
    fun delete(key: String)
}

data class StoredObject(val bytes: ByteArray, val contentType: String) {
    // ByteArray 는 참조로 비교되므로 data class 의 기본 구현을 쓰면 안 된다.
    override fun equals(other: Any?): Boolean =
        this === other || (other is StoredObject && bytes.contentEquals(other.bytes) && contentType == other.contentType)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + contentType.hashCode()
}
