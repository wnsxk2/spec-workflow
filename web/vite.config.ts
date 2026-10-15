/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 화면과 서버를 같은 사이트에서 서비스한다. 개발 중에는 /api 요청을 Spring Boot로 넘겨 같은 주소처럼 맞춘다.
// API_PROXY_TARGET으로 넘길 곳을 바꿀 수 있다 (E2E는 다른 포트의 서버를 쓴다).
const apiProxyTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': apiProxyTarget,
    },
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
})
