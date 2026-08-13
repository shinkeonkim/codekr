/**
 * 멘션의 **저장 표기**와 **보이는 글자** 사이를 오간다 (#214).
 *
 * 저장되는 것은 `@{u:42}` 다 — 사용자 id 를 담으면 닉네임 경계·닉네임 변경·탈퇴가
 * 한꺼번에 풀린다. 반대로 **사람이 보는 것은 늘 `@닉네임`** 이어야 한다: 편집기를
 * 열었을 때 저장 표기가 그대로 보이면 그것이 무엇인지 알 수 없다.
 */
const STORED = /@\{u:(\d+)}/g;

export interface MentionLabel {
  id: number;
  nickname: string;
}

/** 저장 표기 → 사람이 읽는 글자. 이름표가 없으면 표기를 지운다 — 고칠 수 없는 값이다. */
export function toDisplay(body: string, mentions: MentionLabel[]): string {
  const labels = new Map(mentions.map((each) => [each.id, each.nickname]));
  return body.replace(STORED, (_, id: string) => {
    const nickname = labels.get(Number(id));
    return nickname ? `@${nickname}` : "@알 수 없는 사용자";
  });
}

/**
 * 사람이 읽는 글자 → 저장 표기.
 *
 * **자동완성으로 고른 것만 멘션이다.** 손으로 친 `@아무개` 는 그냥 글자로 남는다 —
 * 서버가 이름으로 찾아 바꾸는 방식은 동명이인·닉네임 변경에서 엉뚱한 사람을 가리킨다.
 *
 * 긴 이름부터 바꾼다. `@김철` 과 `@김철수` 가 함께 고른 목록에 있으면, 짧은 것을
 * 먼저 바꿀 때 긴 이름의 앞부분만 잘려 나간다.
 */
export function toStored(text: string, picked: MentionLabel[]): string {
  return [...picked]
    .sort((a, b) => b.nickname.length - a.nickname.length)
    .reduce(
      (body, each) => body.split(`@${each.nickname}`).join(`@{u:${each.id}}`),
      text,
    );
}

/**
 * 지금 커서 앞에서 쓰고 있는 `@질의`.
 *
 * 없으면 null 이다. 공백이 들어가면 끝난 것으로 본다 — 닉네임에 공백이 있으면 그
 * 사람은 자동완성 목록에서 고르는 수밖에 없고, 그것이 이 설계의 전제다.
 */
export function activeQuery(text: string, caret: number): string | null {
  const before = text.slice(0, caret);
  const at = before.lastIndexOf("@");
  if (at < 0) return null;

  const query = before.slice(at + 1);
  if (/[\s@]/.test(query)) return null;
  // `@` 바로 앞이 글자면 이메일 같은 것이다 — 멘션이 아니다.
  if (at > 0 && !/\s/.test(before[at - 1])) return null;
  return query;
}
