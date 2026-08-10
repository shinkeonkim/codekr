/** 서버가 내려주는 사용자 표현. */

export type UserRole = "USER" | "ADMIN";

export interface User {
  id: number;
  email: string;
  nickname: string;
  role: UserRole;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}
