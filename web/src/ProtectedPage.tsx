import { useEffect, useState } from 'react'
import { useAuth } from './auth/AuthContext'

/** 로그인한 사용자만 볼 수 있는 화면. 보호된 요청으로 요청한 사용자의 식별자를 받아 보여 준다. */
export function ProtectedPage() {
  const { auth } = useAuth()
  const [userId, setUserId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    auth
      .authorizedFetch('/api/me')
      .then(async (response) => {
        if (!response.ok) throw new Error(String(response.status))
        const body = (await response.json()) as { userId: string }
        if (!cancelled) setUserId(body.userId)
      })
      .catch(() => {
        if (!cancelled) setError('내 정보를 불러오지 못했습니다')
      })
    return () => {
      cancelled = true
    }
  }, [auth])

  return (
    <main>
      <h1>내 정보</h1>
      {userId && (
        <p>
          사용자 식별자: <span data-testid="user-id">{userId}</span>
        </p>
      )}
      {error && <p role="alert">{error}</p>}
      <button type="button" onClick={() => void auth.logout()}>
        로그아웃
      </button>
    </main>
  )
}
