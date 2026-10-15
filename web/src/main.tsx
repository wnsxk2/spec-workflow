import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { AuthProvider } from './auth/AuthContext.tsx'
import { createAuthClient } from './auth/authClient.ts'

const authClient = createAuthClient()
// 앱이 시작될 때(새로고침, 새 탭 포함) 토큰 교환을 한 번 시도해 로그인 상태를 되살린다.
void authClient.restore()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider client={authClient}>
      <App />
    </AuthProvider>
  </StrictMode>,
)
