import { useAuth } from './auth/AuthContext'
import { LoginPage } from './LoginPage'
import { ProtectedPage } from './ProtectedPage'

export default function App() {
  const { state } = useAuth()
  if (state.status === 'restoring') {
    return (
      <main>
        <p>로그인 상태를 확인하는 중입니다</p>
      </main>
    )
  }
  if (state.status === 'logged-in') return <ProtectedPage />
  return <LoginPage notice={state.notice} />
}
