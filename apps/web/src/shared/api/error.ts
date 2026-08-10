/** 서버가 내려주는 오류 규약을 그대로 감싼 예외. 화면은 message 만 보면 된다. */
export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
    readonly fieldErrors: { field: string; message: string }[] = [],
  ) {
    super(message);
    this.name = "ApiError";
  }
}
