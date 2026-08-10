import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 컨테이너 이미지를 얇게 유지하기 위해 standalone 출력을 쓴다.
  output: "standalone",
  reactStrictMode: true,
};

export default nextConfig;
