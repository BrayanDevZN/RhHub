import { BrowserRouter, Navigate, Outlet, Route, Routes } from "react-router-dom"
import { AuthProvider, useAuth } from "@/lib/auth"
import Home from "@/pages/Home"
import Login from "@/pages/Login"
import Register from "@/pages/Register"

function ProtectedRoute() {
  const { user } = useAuth()
  return user ? <Outlet /> : <Navigate to="/login" replace />
}

function PublicRoute() {
  const { user } = useAuth()
  return user ? <Navigate to="/" replace /> : <Outlet />
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<PublicRoute />}>
            <Route path="/login" element={<Login />} />
            <Route path="/cadastro" element={<Register />} />
          </Route>
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<Home />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
