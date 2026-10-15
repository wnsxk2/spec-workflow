import { randomBytes } from 'node:crypto'
import { defineConfig, devices } from '@playwright/test'

// E2E는 개발용과 다른 포트의 서버·화면을 띄우고, 서버는 e2e 프로필(app_e2e 데이터베이스, Redis 1번)을 쓴다.
// PostgreSQL·Redis는 Docker Compose로 미리 켜 둔다 (README.md 참고). 시간은 앞당기지 않는다.
const SERVER_PORT = 18080
const WEB_PORT = 15173

export default defineConfig({
  testDir: './tests',
  globalSetup: './global-setup.ts',
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://localhost:${WEB_PORT}`,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // 미리 설치된 Chromium을 쓰려면 실행 파일 경로를 넣는다. 비어 있으면 `npx playwright install`로 받은 것을 쓴다.
        launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined },
      },
    },
  ],
  webServer: [
    {
      name: 'server',
      command: './gradlew bootRun --args=--spring.profiles.active=e2e',
      cwd: '../server',
      url: `http://localhost:${SERVER_PORT}/actuator/health`,
      env: {
        SERVER_PORT: String(SERVER_PORT),
        // E2E마다 새로 만드는 서명용 비밀 키. 저장소에 비밀값을 두지 않는다.
        AUTH_JWT_SECRET: randomBytes(48).toString('base64'),
      },
      timeout: 180_000,
      reuseExistingServer: false,
    },
    {
      name: 'web',
      command: `npm run dev -- --port ${WEB_PORT} --strictPort`,
      cwd: '../web',
      url: `http://localhost:${WEB_PORT}`,
      env: { API_PROXY_TARGET: `http://localhost:${SERVER_PORT}` },
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
})
