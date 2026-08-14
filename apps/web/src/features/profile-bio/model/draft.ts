/**
 * "남에게 보이는 프로필" 한 벌을 저장할 때 무엇을 보내고 무엇을 막을지 (#581).
 *
 * 화면에서 떼어 둔다 — **무엇이 바뀌었는가**와 **저장해도 되는가**는 그리기와 무관한
 * 판단이고, 저장 단추 하나가 그 둘에 달려 있다.
 */

/** 서버의 상한과 같아야 한다 (`ProfileEditController`). 넘으면 400 이 오는데, 그 전에 화면이 말한다. */
export const NAME_MIN = 2;
export const NAME_MAX = 30;
export const BIO_MAX = 100;

export interface ProfileDraft {
  displayName: string;
  bio: string;
}

/**
 * 소개 문구를 **서버와 같은 규칙으로** 다듬는다.
 *
 * 서버는 줄 끝 공백을 떼고 앞뒤 빈 줄을 지운다. 여기서 같은 일을 하지 않으면, 끝에
 * 공백 하나를 치고 저장한 뒤 **"바뀐 것이 있다" 가 영원히 남는다** — 화면이 보내는 값과
 * 서버가 돌려주는 값이 다르기 때문이다.
 */
export function cleanBio(raw: string): string {
  return raw
    .split("\n")
    .map((line) => line.trimEnd())
    .join("\n")
    .trim();
}

/**
 * 바뀐 것만 담는다.
 *
 * **전부 보내지 않는다** — 서버는 `null` 인 항목을 "안 바꾼다" 로 읽는다(#104 의 규칙).
 * 전체를 보내면 항목이 늘었을 때 옛 화면이 새 항목을 지운다.
 */
export function changes(draft: ProfileDraft, saved: ProfileDraft): Partial<ProfileDraft> {
  const next: Partial<ProfileDraft> = {};
  const name = draft.displayName.trim();
  if (name !== saved.displayName.trim()) next.displayName = name;
  const bio = cleanBio(draft.bio);
  if (bio !== cleanBio(saved.bio)) next.bio = bio;
  return next;
}

/**
 * 저장을 막아야 하는 이유. 칸 이름 → 사람에게 할 말.
 *
 * **바뀐 칸만 본다.** 지금 이름이 규칙을 어기고 있어도(옛 규칙으로 만든 계정) 소개만
 * 고치는 것까지 막을 이유는 없다.
 */
export function problems(draft: ProfileDraft, saved: ProfileDraft): Partial<ProfileDraft> {
  const found: Partial<ProfileDraft> = {};
  const changed = changes(draft, saved);

  if (changed.displayName !== undefined) {
    const length = changed.displayName.length;
    if (length < NAME_MIN || length > NAME_MAX) {
      found.displayName = `이름은 ${NAME_MIN}자 이상 ${NAME_MAX}자 이하여야 합니다.`;
    }
  }
  // 서버가 세는 것은 다듬기 **전** 길이다 (`@Size` 가 원본에 붙는다).
  if (changed.bio !== undefined && draft.bio.length > BIO_MAX) {
    found.bio = `소개는 ${BIO_MAX}자를 넘을 수 없습니다.`;
  }
  return found;
}

/** 저장할 것이 있고, 막을 이유가 없는가. */
export function canSave(draft: ProfileDraft, saved: ProfileDraft): boolean {
  return (
    Object.keys(changes(draft, saved)).length > 0 &&
    Object.keys(problems(draft, saved)).length === 0
  );
}
