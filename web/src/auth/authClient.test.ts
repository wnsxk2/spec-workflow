import { beforeEach, describe, expect, it, vi } from 'vitest'
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
    server.on('POST', '/api/auth/login', () =>
      json(429, { message: '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요' }),
    )

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
  describe('Access Token 만료 뒤 자동 복구', () => {
    /** 로그인해 메모리에 Access Token(expired-access-token)을 둔 상태를 만든다. */
    async function loggedIn() {
      server.on('POST', '/api/auth/login', () => json(200, { accessToken: 'expired-access-token' }))
      await auth.login('user@example.com', 'correct-password')
    }

    /** 새 Access Token을 붙인 요청에만 성공하는 보호된 요청. */
    function protectedResource(method: string, path: string) {
      server.on(method, path, (request) =>
        request.headers.get('Authorization') === 'Bearer new-access-token'
          ? json(200, { ok: true, received: request.body ?? null })
          : json(401, {}),
      )
    }

    function requestsTo(path: string) {
      return server.requests.filter((request) => request.path === path)
    }

    function rotationCount() {
      return requestsTo('/api/auth/refresh').length
    }

    it('정상 응답에는 토큰 교환을 하지 않는다', async () => {
      server.on('POST', '/api/auth/login', () => json(200, { accessToken: 'new-access-token' }))
      await auth.login('user@example.com', 'correct-password')
      protectedResource('GET', '/api/me')

      const response = await auth.authorizedFetch('/api/me')

      expect(response.status).toBe(200)
      expect(rotationCount()).toBe(0)
    })

    it('401을 받으면 토큰 교환을 한 번 하고, 원래 요청을 메서드와 본문 그대로 새 Access Token으로 한 번 다시 보낸다', async () => {
      await loggedIn()
      server.on('POST', '/api/auth/refresh', () => json(200, { accessToken: 'new-access-token' }))
      protectedResource('POST', '/api/notes')

      const response = await auth.authorizedFetch('/api/notes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: '메모' }),
      })

      expect(response.status).toBe(200)
      expect(await response.json()).toEqual({ ok: true, received: { text: '메모' } })
      expect(rotationCount()).toBe(1)
      const notes = requestsTo('/api/notes')
      expect(notes.map((request) => request.headers.get('Authorization'))).toEqual([
        'Bearer expired-access-token',
        'Bearer new-access-token',
      ])
      expect(notes[1].headers.get('Content-Type')).toBe('application/json')
      expect(auth.getState()).toEqual({ status: 'logged-in' })
    })

    it('다시 보낸 요청도 401이면 토큰 교환과 재요청을 반복하지 않는다', async () => {
      await loggedIn()
      server.on('POST', '/api/auth/refresh', () => json(200, { accessToken: 'new-access-token' }))
      server.on('GET', '/api/me', () => json(401, {}))

      const response = await auth.authorizedFetch('/api/me')

      expect(response.status).toBe(401)
      expect(rotationCount()).toBe(1)
      expect(requestsTo('/api/me')).toHaveLength(2)
    })

    it('한 탭에서 여러 요청이 동시에 401을 받으면 토큰 교환 하나를 함께 기다리고, 각 요청을 한 번씩 다시 보낸다', async () => {
      await loggedIn()
      let finishRotation!: () => void
      server.on(
        'POST',
        '/api/auth/refresh',
        () =>
          new Promise((resolve) => (finishRotation = () => resolve(json(200, { accessToken: 'new-access-token' })))),
      )
      for (const path of ['/api/a', '/api/b', '/api/c']) protectedResource('GET', path)

      const pending = ['/api/a', '/api/b', '/api/c'].map((path) => auth.authorizedFetch(path))
      await vi.waitFor(() => expect(rotationCount()).toBe(1))
      finishRotation()
      const responses = await Promise.all(pending)

      expect(responses.map((response) => response.status)).toEqual([200, 200, 200])
      expect(rotationCount()).toBe(1)
      for (const path of ['/api/a', '/api/b', '/api/c']) {
        expect(requestsTo(path)).toHaveLength(2)
      }
    })

    it('토큰 교환이 끝난 뒤에 도착한 이전 Access Token 요청의 401은 새로 교환하지 않고 새 Access Token으로 다시 보낸다', async () => {
      await loggedIn()
      server.on('POST', '/api/auth/refresh', () => json(200, { accessToken: 'new-access-token' }))
      let answerSlowRequest!: () => void
      server.on('GET', '/api/slow', (request) =>
        request.headers.get('Authorization') === 'Bearer new-access-token'
          ? json(200, {})
          : new Promise((resolve) => (answerSlowRequest = () => resolve(json(401, {})))),
      )
      protectedResource('GET', '/api/me')

      const slow = auth.authorizedFetch('/api/slow')
      expect((await auth.authorizedFetch('/api/me')).status).toBe(200)
      answerSlowRequest()

      expect((await slow).status).toBe(200)
      expect(rotationCount()).toBe(1)
    })

    it('Access Token 없이 보낸 요청의 401에는 토큰 교환을 하지 않는다', async () => {
      server.on('GET', '/api/me', () => json(401, {}))

      const response = await auth.authorizedFetch('/api/me')

      expect(response.status).toBe(401)
      expect(rotationCount()).toBe(0)
    })

    it('토큰 교환이 401이면 Access Token을 지우고 로그아웃 상태를 알리며, 기다리던 요청은 다시 보내지 않는다', async () => {
      await loggedIn()
      server.on('POST', '/api/auth/refresh', () => json(401, { message: '다시 로그인해 주세요' }))
      for (const path of ['/api/a', '/api/b']) protectedResource('GET', path)
      const notified: string[] = []
      auth.subscribe(() => notified.push(auth.getState().status))

      const responses = await Promise.all(['/api/a', '/api/b'].map((path) => auth.authorizedFetch(path)))

      expect(responses.map((response) => response.status)).toEqual([401, 401])
      expect(rotationCount()).toBe(1)
      for (const path of ['/api/a', '/api/b']) {
        expect(requestsTo(path)).toHaveLength(1)
      }
      expect(auth.getState()).toEqual({ status: 'logged-out' })
      expect(notified).toEqual(['logged-out'])

      await auth.authorizedFetch('/api/a')
      expect(server.requests.at(-1)!.headers.get('Authorization')).toBeNull()
    })

    it('토큰 교환에 실패해 로그아웃된 뒤에 도착한 401에는 다시 토큰 교환을 하지 않는다', async () => {
      await loggedIn()
      server.on('POST', '/api/auth/refresh', () => json(401, {}))
      let answerSlowRequest!: () => void
      server.on('GET', '/api/slow', () => new Promise((resolve) => (answerSlowRequest = () => resolve(json(401, {})))))
      protectedResource('GET', '/api/me')

      const slow = auth.authorizedFetch('/api/slow')
      await auth.authorizedFetch('/api/me')
      answerSlowRequest()

      expect((await slow).status).toBe(401)
      expect(rotationCount()).toBe(1)
    })

    it('토큰 교환이 503이면 로그아웃 상태와 함께 잠시 후 다시 시도 안내를 알린다', async () => {
      await loggedIn()
      server.on('POST', '/api/auth/refresh', () => json(503, { message: '잠시 후 다시 시도해 주세요' }))
      protectedResource('GET', '/api/me')

      expect((await auth.authorizedFetch('/api/me')).status).toBe(401)

      expect(auth.getState()).toEqual({ status: 'logged-out', notice: '잠시 후 다시 시도해 주세요' })
    })

    it('토큰 교환 중 서버에 닿지 못해도 로그아웃 상태와 함께 잠시 후 다시 시도 안내를 알린다', async () => {
      const fake = createFakeServer()
      fake.on('POST', '/api/auth/login', () => json(200, { accessToken: 'expired-access-token' }))
      fake.on('GET', '/api/me', () => json(401, {}))
      auth = createAuthClient({
        fetch: (input, init) =>
          input === '/api/auth/refresh' ? Promise.reject(new TypeError('Failed to fetch')) : fake.fetch(input, init),
      })
      await auth.login('user@example.com', 'correct-password')

      expect((await auth.authorizedFetch('/api/me')).status).toBe(401)

      expect(auth.getState()).toEqual({ status: 'logged-out', notice: '잠시 후 다시 시도해 주세요' })
    })
  })

  describe('로그아웃하기', () => {
    async function loggedIn() {
      server.on('POST', '/api/auth/login', () => json(200, { accessToken: 'access-token-1' }))
      await auth.login('user@example.com', 'correct-password')
    }

    it('서버에 로그아웃을 요청하고, 응답을 기다리기 전에 메모리의 Access Token을 지우며, 로그아웃 상태를 알린다', async () => {
      await loggedIn()
      let finishLogout!: () => void
      server.on(
        'POST',
        '/api/auth/logout',
        () => new Promise((resolve) => (finishLogout = () => resolve(new Response(null, { status: 204 })))),
      )
      server.on('GET', '/api/me', () => json(401, {}))
      const notified: string[] = []
      auth.subscribe(() => notified.push(auth.getState().status))

      const logout = auth.logout()
      await vi.waitFor(() => expect(server.requests.at(-1)!.path).toBe('/api/auth/logout'))
      await auth.authorizedFetch('/api/me')
      expect(server.requests.at(-1)!.headers.get('Authorization')).toBeNull()
      finishLogout()

      expect(await logout).toEqual({ ok: true })
      expect(auth.getState()).toEqual({ status: 'logged-out' })
      expect(notified).toEqual(['logged-out'])
    })

    it('서버가 실패 응답을 주어도 Access Token을 지우고 로그아웃 상태를 알리되, 쿠키 삭제까지 성공했다고 보지 않는다', async () => {
      await loggedIn()
      server.on('POST', '/api/auth/logout', () => json(500, {}))
      server.on('GET', '/api/me', () => json(401, {}))
      const notified: string[] = []
      auth.subscribe(() => notified.push(auth.getState().status))

      const result = await auth.logout()
      await auth.authorizedFetch('/api/me')

      expect(result).toEqual({ ok: false })
      expect(auth.getState()).toEqual({ status: 'logged-out', notice: expect.any(String) })
      expect(notified).toContain('logged-out')
      expect(server.requests.at(-1)!.headers.get('Authorization')).toBeNull()
    })

    it('서버에 닿지 못해도 Access Token을 지우고 로그아웃 상태를 알리되, 쿠키 삭제까지 성공했다고 보지 않는다', async () => {
      const fake = createFakeServer()
      fake.on('POST', '/api/auth/login', () => json(200, { accessToken: 'access-token-1' }))
      fake.on('GET', '/api/me', () => json(401, {}))
      auth = createAuthClient({
        fetch: (input, init) =>
          input === '/api/auth/logout' ? Promise.reject(new TypeError('Failed to fetch')) : fake.fetch(input, init),
      })
      await auth.login('user@example.com', 'correct-password')

      const notified: string[] = []
      auth.subscribe(() => notified.push(auth.getState().status))

      const result = await auth.logout()
      await auth.authorizedFetch('/api/me')

      expect(result).toEqual({ ok: false })
      expect(auth.getState()).toEqual({ status: 'logged-out', notice: expect.any(String) })
      expect(notified).toContain('logged-out')
      expect(fake.requests.at(-1)!.headers.get('Authorization')).toBeNull()
    })

    it('로그아웃하는 동안 진행 중이던 토큰 교환이 늦게 끝나도 로그인 상태로 되돌아가지 않는다', async () => {
      await loggedIn()
      let finishRotation!: () => void
      server.on(
        'POST',
        '/api/auth/refresh',
        () => new Promise((resolve) => (finishRotation = () => resolve(json(200, { accessToken: 'access-token-2' })))),
      )
      server.on('GET', '/api/me', () => json(401, {}))
      server.on('POST', '/api/auth/logout', () => new Response(null, { status: 204 }))

      const pending = auth.authorizedFetch('/api/me')
      await vi.waitFor(() => expect(server.requests.at(-1)!.path).toBe('/api/auth/refresh'))
      await auth.logout()
      finishRotation()
      await pending

      expect(auth.getState()).toEqual({ status: 'logged-out' })
      await auth.authorizedFetch('/api/me')
      expect(server.requests.at(-1)!.headers.get('Authorization')).toBeNull()
    })
  })
})
