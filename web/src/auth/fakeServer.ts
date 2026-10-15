/** 화면 인증 모듈 테스트용 가짜 서버. 받은 요청을 기록하고 정해 둔 응답을 돌려준다. */
export type RecordedRequest = { method: string; path: string; headers: Headers; body: unknown }

type Handler = (request: RecordedRequest) => Response | Promise<Response>

export function createFakeServer() {
  const routes = new Map<string, Handler>()
  const requests: RecordedRequest[] = []

  const fetch = async (input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> => {
    const method = (init.method ?? 'GET').toUpperCase()
    const path = typeof input === 'string' ? input : input instanceof URL ? input.pathname : new URL(input.url).pathname
    const body = typeof init.body === 'string' ? JSON.parse(init.body) : undefined
    const request = { method, path, headers: new Headers(init.headers), body }
    requests.push(request)
    const handler = routes.get(`${method} ${path}`)
    if (!handler) return new Response(null, { status: 404 })
    return handler(request)
  }

  return {
    fetch,
    requests,
    on(method: string, path: string, handler: Handler) {
      routes.set(`${method} ${path}`, handler)
    },
  }
}

export function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
