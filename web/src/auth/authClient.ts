/**
 * 화면 인증 모듈. 화면의 나머지 부분은 이 모듈만 통해 로그인을 다룬다.
 * Access Token은 이 모듈의 메모리에만 둔다. localStorage나 쿠키에 두지 않는다.
 * 로그아웃(05)은 이후 티켓에서 더한다.
 */
export type AuthState =
  /** 앱 시작 시 토큰 교환으로 로그인 상태를 되살리는 중이다. */
  | { status: 'restoring' }
  | { status: 'logged-in' }
  /** notice가 있으면 화면은 로그인 화면에서 그 안내를 함께 보여 준다. */
  | { status: 'logged-out'; notice?: string }

/** 로그인 실패의 종류. 화면은 종류마다 다르게 안내한다. */
export type LoginFailure = 'invalid-credentials' | 'login-lockout' | 'temporarily-unavailable'

export type LoginResult = { ok: true } | { ok: false; failure: LoginFailure; message: string }

export type RestoreResult = { ok: boolean }

const DEFAULT_MESSAGES: Record<LoginFailure, string> = {
  'invalid-credentials': '이메일 또는 비밀번호가 올바르지 않습니다',
  'login-lockout': '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요',
  'temporarily-unavailable': '잠시 후 다시 시도해 주세요',
}

export interface AuthClient {
  /** 이메일과 비밀번호로 로그인한다. */
  login(email: string, password: string): Promise<LoginResult>
  /**
   * 로그인 상태 복원. 앱이 시작될 때(새로고침, 새 탭 포함) 토큰 교환을 한 번 시도해 로그인 상태를 되살린다.
   * 여러 번 불러도 토큰 교환은 한 번만 하고 같은 결과를 돌려준다.
   */
  restore(): Promise<RestoreResult>
  /**
   * 인증 요청 보내기. 로그인한 사용자로 요청하며 요청에 Access Token을 붙인다.
   * 401을 받으면 토큰 교환을 한 번 하고 같은 요청을 한 번 다시 보낸다. 교환에 실패하면 로그아웃 상태가 된다.
   */
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
  let restoreInFlight: Promise<RestoreResult> | null = null
  /** 진행 중인 토큰 교환. 한 탭 안에서 동시에 교환이 필요하면 이 하나를 함께 기다린다. */
  let rotationInFlight: Promise<boolean> | null = null

  function setState(next: AuthState) {
    state = next
    listeners.forEach((listener) => listener())
  }

  async function restoreOnce(): Promise<RestoreResult> {
    setState({ status: 'restoring' })
    return { ok: await rotate() }
  }

  /**
   * 토큰 교환. 성공하면 메모리의 Access Token을 새 값으로 바꾸고 로그인 상태가 된다.
   * 실패하면 Access Token을 지우고 로그아웃 상태가 된다. 401이 아닌 실패에는 잠시 후 다시 시도 안내를 싣는다.
   * 이미 진행 중인 토큰 교환이 있으면 새로 시작하지 않고 그 결과를 함께 기다린다.
   */
  function rotate(): Promise<boolean> {
    rotationInFlight ??= rotateOnce().finally(() => {
      rotationInFlight = null
    })
    return rotationInFlight
  }

  async function rotateOnce(): Promise<boolean> {
    let response: Response
    try {
      response = await fetch('/api/auth/refresh', { method: 'POST' })
    } catch {
      return becomeLoggedOut(DEFAULT_MESSAGES['temporarily-unavailable'])
    }

    if (response.status === 401) return becomeLoggedOut()
    if (!response.ok) return becomeLoggedOut((await messageOf(response)) ?? DEFAULT_MESSAGES['temporarily-unavailable'])
    const body = (await response.json()) as { accessToken: string }
    accessToken = body.accessToken
    // 이미 로그인 상태(401 뒤 자동 복구)면 로그인 상태가 바뀌지 않았으므로 알리지 않는다.
    if (state.status !== 'logged-in') setState({ status: 'logged-in' })
    return true
  }

  /** 메모리의 Access Token을 지우고 로그아웃 상태를 알린다. notice는 로그인 화면에 함께 보여 줄 안내다. */
  function becomeLoggedOut(notice?: string): false {
    accessToken = null
    setState(notice ? { status: 'logged-out', notice } : { status: 'logged-out' })
    return false
  }

  /** 지금 메모리의 Access Token을 붙여 보낸다. 어떤 Access Token을 붙였는지도 함께 돌려준다. */
  async function send(path: string, init: RequestInit): Promise<{ response: Response; sentWith: string | null }> {
    const sentWith = accessToken
    const headers = new Headers(init.headers)
    if (sentWith) headers.set('Authorization', `Bearer ${sentWith}`)
    return { response: await fetch(path, { ...init, headers }), sentWith }
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

    restore() {
      restoreInFlight ??= restoreOnce()
      return restoreInFlight
    },

    async authorizedFetch(path, init = {}) {
      const { response, sentWith } = await send(path, init)
      if (response.status !== 401 || sentWith === null) return response
      // 그 사이 다른 요청의 토큰 교환이 실패해 로그아웃되었으면 다시 교환하지 않는다.
      if (accessToken === null) return response
      // 그 사이 다른 요청의 토큰 교환으로 Access Token이 이미 바뀌었으면 교환하지 않고 바로 다시 보낸다.
      const alreadyRotated = accessToken !== sentWith
      if (!alreadyRotated && !(await rotate())) return response
      return (await send(path, init)).response
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
