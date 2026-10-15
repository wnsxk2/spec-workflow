/**
 * 화면 인증 모듈. 화면의 나머지 부분은 이 모듈만 통해 로그인을 다룬다.
 * Access Token은 이 모듈의 메모리에만 둔다. localStorage나 쿠키에 두지 않는다.
 * 토큰 교환·로그인 상태 복원(02), 401 뒤 자동 복구(03), 로그아웃(05)은 이후 티켓에서 더한다.
 */
export type AuthState = { status: 'logged-out' } | { status: 'logged-in' }

/** 로그인 실패의 종류. 화면은 종류마다 다르게 안내한다. */
export type LoginFailure = 'invalid-credentials' | 'login-lockout' | 'temporarily-unavailable'

export type LoginResult = { ok: true } | { ok: false; failure: LoginFailure; message: string }

const DEFAULT_MESSAGES: Record<LoginFailure, string> = {
  'invalid-credentials': '이메일 또는 비밀번호가 올바르지 않습니다',
  'login-lockout': '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요',
  'temporarily-unavailable': '잠시 후 다시 시도해 주세요',
}

export interface AuthClient {
  /** 이메일과 비밀번호로 로그인한다. */
  login(email: string, password: string): Promise<LoginResult>
  /** 로그인한 사용자로 요청한다. 요청에 Access Token을 붙인다. */
  authorizedFetch(path: string, init?: RequestInit): Promise<Response>
  getState(): AuthState
  /** 로그인 상태가 바뀔 때마다 알림을 받는다. 반환한 함수를 부르면 그만 받는다. */
  subscribe(listener: () => void): () => void
}

type Fetch = (input: string, init?: RequestInit) => Promise<Response>

export function createAuthClient({ fetch = globalThis.fetch.bind(globalThis) }: { fetch?: Fetch } = {}): AuthClient {
  let accessToken: string | null = null
  let state: AuthState = { status: 'logged-out' }
  const listeners = new Set<() => void>()

  function setState(next: AuthState) {
    state = next
    listeners.forEach((listener) => listener())
  }

  return {
    async login(email, password) {
      let response: Response
      try {
        response = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password }),
        })
      } catch {
        return failed('temporarily-unavailable')
      }

      if (!response.ok) {
        return failed(failureOf(response.status), await messageOf(response))
      }
      const body = (await response.json()) as { accessToken: string }
      accessToken = body.accessToken
      setState({ status: 'logged-in' })
      return { ok: true }
    },

    authorizedFetch(path, init = {}) {
      const headers = new Headers(init.headers)
      if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
      return fetch(path, { ...init, headers })
    },

    getState: () => state,

    subscribe(listener) {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
  }
}

function failureOf(status: number): LoginFailure {
  if (status === 401) return 'invalid-credentials'
  if (status === 429) return 'login-lockout'
  return 'temporarily-unavailable'
}

/** 서버가 보낸 안내 메시지. 읽을 수 없으면 undefined. */
async function messageOf(response: Response): Promise<string | undefined> {
  try {
    const body = (await response.json()) as { message?: unknown }
    return typeof body.message === 'string' ? body.message : undefined
  } catch {
    return undefined
  }
}

function failed(failure: LoginFailure, message?: string): LoginResult {
  return { ok: false, failure, message: message ?? DEFAULT_MESSAGES[failure] }
}
