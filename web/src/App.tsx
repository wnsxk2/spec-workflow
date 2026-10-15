import { useAuth } from './auth/AuthContext'
import { LoginPage } from './LoginPage'
import { ProtectedPage } from './ProtectedPage'

export default function App() {
  const { state } = useAuth()
  return state.status === 'logged-in' ? <ProtectedPage /> : <LoginPage />
}
