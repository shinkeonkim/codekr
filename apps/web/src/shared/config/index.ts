/** 서버 주소. 배포 환경마다 다르므로 빌드 시점 환경 변수로 받는다. */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:18080";
export const WS_BASE_URL = process.env.NEXT_PUBLIC_WS_BASE_URL ?? "ws://localhost:18080";
