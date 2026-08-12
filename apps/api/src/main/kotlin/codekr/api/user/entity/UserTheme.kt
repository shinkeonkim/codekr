package codekr.api.user.entity

/**
 * 계정에 저장하는 화면 테마 (#274).
 *
 * 기기(localStorage)에도 남는다 — 로그인 없이도 되어야 하기 때문이다 (#206).
 * **여기 있는 값은 기기를 옮겨도 따라오는 값**이고, 없으면 기기의 선택을 그대로 쓴다.
 */
enum class UserTheme { LIGHT, DARK, SYSTEM }
