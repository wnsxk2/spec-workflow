import { createContext, useContext, useSyncExternalStore, type ReactNode } from 'react'
import type { AuthClient, AuthState } from './authClient'

const AuthContext = createContext<AuthClient | null>(null)

export function AuthProvider({ client, children }: { client: AuthClient; children: ReactNode }) {
  return <AuthContext.Provider value={client}>{children}</AuthContext.Provider>
}

/** 화면 인증 모듈. 로그인 상태가 바뀌면 이 hook을 쓰는 화면이 다시 그려진다. */
// eslint-disable-next-line react/only-export-components
export function useAuth(): { auth: AuthClient; state: AuthState } {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('AuthProvider 안에서만 useAuth를 쓸 수 있습니다')
  const state = useSyncExternalStore(auth.subscribe, auth.getState)
  return { auth, state }
}
