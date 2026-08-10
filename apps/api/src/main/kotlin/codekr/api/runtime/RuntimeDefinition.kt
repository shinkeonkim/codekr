package codekr.api.runtime

/** 실행 환경 하나의 화면용 정보. 실제 실행 방법(이미지·명령)은 실행기만 알면 된다. */
data class RuntimeDefinition(
    val id: String,
    val label: String,
    val monacoLanguage: String,
    val template: String,
)
