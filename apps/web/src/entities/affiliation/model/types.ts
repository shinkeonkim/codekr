export type AffiliationKind = "SCHOOL" | "COMPANY";

/** 내게 붙은 소속 (#398). */
export interface AttachedAffiliation {
  affiliationId: number;
  name: string;
  kind: AffiliationKind;
  kindLabel: string;
  /**
   * 어느 주소로 붙었는지.
   *
   * **남에게는 보이지 않는다** — 내 화면에서만 "어느 메일 때문에 붙었나" 를 답한다.
   */
  email: string;
}

/** 확인한 주소의 도메인이 가리키는데 아직 안 붙인 소속 (#398). */
export interface AttachableAffiliation {
  affiliationId: number;
  name: string;
  kindLabel: string;
  email: string;
}

export interface MyAffiliations {
  attached: AttachedAffiliation[];
  attachable: AttachableAffiliation[];
}
