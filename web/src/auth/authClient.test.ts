import { beforeEach, describe, expect, it } from 'vitest'
import { createAuthClient, type AuthClient } from './authClient'
import { createFakeServer, json } from './fakeServer'

describe('화면 인증 모듈', () => {
  let server: ReturnType<typeof createFakeServer>
  let auth: AuthClient

  beforeEach(() => {
    server = createFakeServer()
    auth = createAuthClient({ fetch: server.fetch })
  })

  it('로그인에 성공하면 로그인 상태가 되고 화면에 알린다', async () => {
    server.on('POST', '/api/auth/login', () => json(200, { accessToken: 'access-token-1' }))
    const notified: string[] = []
    auth.subscribe(() => notified.push(auth.getState().status))

    const result = await auth.login('user@example.com', 'correct-password')

    expect(result).toEqual({ ok: true })
    expect(auth.getState()).toEqual({ status: 'logged-in' })
    expect(notified).toEqual(['logged-in'])
    expect(server.requests[0].body).toEqual({ email: 'user@example.com', password: 'correct-password' })
  })
  it('인증 요청 보내기는 메모리의 Access Token을 Bearer 방식으로 붙인다', async () => {
    server.on('POST', '/api/auth/login', () => json(200, { accessToken: 'access-token-1' }))
    server.on('GET', '/api/me', () => json(200, { userId: 'user-1' }))
    await auth.login('user@example.com', 'correct-password')

    const response = await auth.authorizedFetch('/api/me')

    expect(await response.json()).toEqual({ userId: 'user-1' })
    expect(server.requests[1].headers.get('Authorization')).toBe('Bearer access-token-1')
  })
  it('없는 이메일이나 틀린 비밀번호는 공통 실패로 알리고 서버의 안내 메시지를 그대로 돌려준다', async () => {
    server.on('POST', '/api/auth/login', () => json(401, { message: '이메일 또는 비밀번호가 올바르지 않습니다' }))
    const notified: string[] = []
    auth.subscribe(() => notified.push(auth.getState().status))

    const result = await auth.login('user@example.com', 'wrong-password')

    expect(result).toEqual({
      ok: false,
      failure: 'invalid-credentials',
      message: '이메일 또는 비밀번호가 올바르지 않습니다',
    })
    expect(auth.getState()).toEqual({ status: 'logged-out' })
    expect(notified).toEqual([])
  })

  it('로그인 차단은 공통 실패와 구분해 알린다', async () => {
    server.on('POST', '/api/auth/login', () => json(429, { message: '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요' }))

    const result = await auth.login('user@example.com', 'correct-password')

    expect(result).toEqual({
      ok: false,
      failure: 'login-lockout',
      message: '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요',
    })
  })

  it('서버가 일시적으로 처리하지 못하면(503) 잠시 후 다시 시도로 알린다', async () => {
    server.on('POST', '/api/auth/login', () => json(503, { message: '잠시 후 다시 시도해 주세요' }))

    const result = await auth.login('user@example.com', 'correct-password')

    expect(result).toEqual({ ok: false, failure: 'temporarily-unavailable', message: '잠시 후 다시 시도해 주세요' })
    expect(auth.getState()).toEqual({ status: 'logged-out' })
  })

  it('서버에 닿지 못해도 잠시 후 다시 시도로 알린다', async () => {
    auth = createAuthClient({ fetch: () => Promise.reject(new TypeError('Failed to fetch')) })

    const result = await auth.login('user@example.com', 'correct-password')

    expect(result).toEqual({ ok: false, failure: 'temporarily-unavailable', message: '잠시 후 다시 시도해 주세요' })
  })

  it('로그인에 실패하면 인증 요청에 Access Token을 붙이지 않는다', async () => {
    server.on('POST', '/api/auth/login', () => json(401, { message: '이메일 또는 비밀번호가 올바르지 않습니다' }))
    server.on('GET', '/api/me', () => json(401, {}))
    await auth.login('user@example.com', 'wrong-password')

    await auth.authorizedFetch('/api/me')

    expect(server.requests[1].headers.get('Authorization')).toBeNull()
  })
  describe('로그인 상태 복원', () => {
    it('앱 시작 시 토큰 교환으로 로그인 상태를 복원하고, 받은 Access Token으로 인증 요청을 보낸다', async () => {
      server.on('POST', '/api/auth/refresh', () => json(200, { accessToken: 'restored-access-token' }))
      server.on('GET', '/api/me', () => json(200, { userId: 'user-1' }))
      const notified: string[] = []
      auth.subscribe(() => notified.push(auth.getState().status))

      const result = await auth.restore()
      await auth.authorizedFetch('/api/me')

      expect(result).toEqual({ ok: true })
      expect(auth.getState()).toEqual({ status: 'logged-in' })
      expect(notified).toEqual(['restoring', 'logged-in'])
      expect(server.requests[1].headers.get('Authorization')).toBe('Bearer restored-access-token')
    })

    it('여러 번 불러도 토큰 교환은 한 번만 시도한다', async () => {
      server.on('POST', '/api/auth/refresh', () => json(200, { accessToken: 'restored-access-token' }))

      await Promise.all([auth.restore(), auth.restore()])
      await auth.restore()

      expect(server.requests.filter((request) => request.path === '/api/auth/refresh')).toHaveLength(1)
    })

    it('토큰 교환이 401이면 Access Token 없이 로그아웃 상태를 알린다', async () => {
      server.on('POST', '/api/auth/refresh', () => json(401, { message: '다시 로그인해 주세요' }))
      server.on('GET', '/api/me', () => json(401, {}))

      const result = await auth.restore()
      await auth.authorizedFetch('/api/me')

      expect(result).toEqual({ ok: false })
      expect(auth.getState()).toEqual({ status: 'logged-out' })
      expect(server.requests[1].headers.get('Authorization')).toBeNull()
    })

    it('토큰 교환이 503이면 로그아웃 상태와 함께 잠시 후 다시 시도 안내를 알린다', async () => {
      server.on('POST', '/api/auth/refresh', () => json(503, { message: '잠시 후 다시 시도해 주세요' }))

      await auth.restore()

      expect(auth.getState()).toEqual({ status: 'logged-out', notice: '잠시 후 다시 시도해 주세요' })
    })

    it('서버에 닿지 못해도 로그아웃 상태와 함께 잠시 후 다시 시도 안내를 알린다', async () => {
      auth = createAuthClient({ fetch: () => Promise.reject(new TypeError('Failed to fetch')) })

      await auth.restore()

      expect(auth.getState()).toEqual({ status: 'logged-out', notice: '잠시 후 다시 시도해 주세요' })
    })
  })
})
