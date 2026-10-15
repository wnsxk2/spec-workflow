import { useState, type FormEvent } from 'react'
import { useAuth } from './auth/AuthContext'

/** notice: 로그인 상태 복원이 실패한 까닭처럼, 로그인 화면에 함께 보여 줄 안내. */
export function LoginPage({ notice }: { notice?: string }) {
  const { auth } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(notice ?? null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    const result = await auth.login(email, password)
    setSubmitting(false)
    if (!result.ok) setError(result.message)
  }

  return (
    <main>
      <h1>로그인</h1>
      <form onSubmit={submit}>
        <label>
          이메일
          <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          비밀번호
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={submitting}>
          로그인
        </button>
      </form>
    </main>
  )
}
