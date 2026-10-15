import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { AuthProvider } from './auth/AuthContext.tsx'
import { createAuthClient } from './auth/authClient.ts'

const authClient = createAuthClient()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider client={authClient}>
      <App />
    </AuthProvider>
  </StrictMode>,
)
