import { BrowserRouter, Navigate, Outlet, Route, Routes } from "react-router-dom"
import { AppLayout } from "@/components/AppLayout"
import { Spinner } from "@/components/ui/spinner"
import { Toaster } from "@/components/ui/toast"
import { TooltipProvider } from "@/components/ui/tooltip"
import { AuthProvider, useAuth } from "@/lib/auth"
import AdminResumes from "@/pages/AdminResumes"
import AdminUsers from "@/pages/AdminUsers"
import Dashboard from "@/pages/Dashboard"
import Login from "@/pages/Login"
import MyResumes from "@/pages/MyResumes"
import Profile from "@/pages/Profile"
import Register from "@/pages/Register"
import Vacancies from "@/pages/Vacancies"

function Loading() {
  return (
    <div className="flex min-h-svh items-center justify-center">
      <Spinner className="size-6" />
    </div>
  )
}

function ProtectedRoute() {
  const { user, loading } = useAuth()
  if (loading) return <Loading />
  return user ? <Outlet /> : <Navigate to="/login" replace />
}

function AdminRoute() {
  const { user } = useAuth()
  return user?.role === "admin" ? <Outlet /> : <Navigate to="/" replace />
}

function PublicRoute() {
  const { user, loading } = useAuth()
  if (loading) return <Loading />
  // A conta de demonstracao tambem pode abrir os formularios de autenticacao.
  return user && user.email !== "demo@rhhub.local" ? <Navigate to="/" replace /> : <Outlet />
}

export default function App() {
  return (
    <Toaster>
      <TooltipProvider>
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              <Route element={<PublicRoute />}>
                <Route path="/login" element={<Login />} />
                <Route path="/cadastro" element={<Register />} />
              </Route>
              <Route element={<ProtectedRoute />}>
                <Route element={<AppLayout />}>
                  <Route path="/" element={<Dashboard />} />
                  <Route path="/vagas" element={<Vacancies />} />
                  <Route path="/candidaturas" element={<MyResumes />} />
                  <Route path="/perfil" element={<Profile />} />
                  <Route element={<AdminRoute />}>
                    <Route path="/admin/usuarios" element={<AdminUsers />} />
                    <Route path="/admin/candidaturas" element={<AdminResumes />} />
                  </Route>
                </Route>
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </TooltipProvider>
    </Toaster>
  )
}
