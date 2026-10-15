import { expect, test, type BrowserContext, type Page } from '@playwright/test'

// e2e 프로필이 E2E용 데이터베이스에 준비하는 사용자 (server/src/main/resources/db/seed/e2e).
const USER = { email: 'e2e-user@example.com', password: 'e2e-password-1234' }
const LOGIN_FAILURE_MESSAGE = '이메일 또는 비밀번호가 올바르지 않습니다'

async function refreshTokenCookie(context: BrowserContext) {
  return (await context.cookies()).find((cookie) => cookie.name === 'refresh_token')?.value
}

async function login(page: Page, email: string, password: string) {
  await page.goto('/')
  await page.getByLabel('이메일').fill(email)
  await page.getByLabel('비밀번호').fill(password)
  await page.getByRole('button', { name: '로그인' }).click()
}

test('시나리오 1: 로그인하면 로그인한 사용자만 볼 수 있는 화면이 보인다', async ({ page, context }) => {
  await login(page, USER.email, USER.password)

  await expect(page.getByRole('heading', { name: '내 정보' })).toBeVisible()
  await expect(page.getByTestId('user-id')).toHaveText(/^[0-9a-f-]{36}$/)

  // Access Token은 메모리에만 두고, Refresh Token은 화면 스크립트가 읽지 못하는 쿠키에 둔다.
  const storage = await page.evaluate(() => ({
    localStorage: localStorage.length,
    sessionStorage: sessionStorage.length,
    scriptVisibleCookies: document.cookie,
  }))
  expect(storage).toEqual({ localStorage: 0, sessionStorage: 0, scriptVisibleCookies: '' })
  const refreshTokenCookie = (await context.cookies()).find((cookie) => cookie.name === 'refresh_token')
  expect(refreshTokenCookie).toMatchObject({ httpOnly: true, secure: true, sameSite: 'Strict', path: '/api/auth' })
})

test('시나리오 2: 로그인 후 새로고침해도 토큰 교환으로 로그인 상태가 유지되어 같은 화면이 보인다', async ({ page, context }) => {
  await login(page, USER.email, USER.password)
  const userId = page.getByTestId('user-id')
  await expect(userId).toHaveText(/^[0-9a-f-]{36}$/)
  const loggedInUserId = await userId.textContent()
  const refreshTokenBefore = await refreshTokenCookie(context)

  await page.reload()

  await expect(page.getByRole('heading', { name: '내 정보' })).toBeVisible()
  await expect(page.getByTestId('user-id')).toHaveText(loggedInUserId!)
  // 새로고침할 때 토큰 교환이 일어나 새 Refresh Token을 받았다.
  expect(await refreshTokenCookie(context)).not.toBe(refreshTokenBefore)
})

test('시나리오 3: 로그인 후 같은 화면을 탭 5개로 동시에 열어도 모든 탭이 로그인 상태이고, 이후 아무 탭이나 새로고침해도 유지된다', async ({
  page,
  context,
}) => {
  await login(page, USER.email, USER.password)
  await expect(page.getByTestId('user-id')).toHaveText(/^[0-9a-f-]{36}$/)
  const loggedInUserId = (await page.getByTestId('user-id').textContent())!

  // 탭 5개가 같은 Refresh Token으로 거의 동시에 토큰 교환한다. 유예 시간이 같은 새 Refresh Token을 돌려준다.
  const tabs = await Promise.all(Array.from({ length: 5 }, () => context.newPage()))
  await Promise.all(tabs.map((tab) => tab.goto('/')))
  for (const tab of tabs) {
    await expect(tab.getByTestId('user-id')).toHaveText(loggedInUserId)
  }

  await tabs[2].reload()
  await expect(tabs[2].getByTestId('user-id')).toHaveText(loggedInUserId)
  await page.reload()
  await expect(page.getByTestId('user-id')).toHaveText(loggedInUserId)
})

test('시나리오 5: 틀린 비밀번호와 존재하지 않는 이메일은 같은 안내를 보여 준다', async ({ page }) => {
  await login(page, USER.email, 'wrong-password')
  await expect(page.getByRole('alert')).toHaveText(LOGIN_FAILURE_MESSAGE)

  await login(page, 'nobody@example.com', USER.password)
  await expect(page.getByRole('alert')).toHaveText(LOGIN_FAILURE_MESSAGE)
  await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible()
})
