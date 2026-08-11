import { http } from './http'
import type { AdminLoginPayload, AdminLoginResponse, AdminUser } from '@/types/admin'

export const login = (payload: AdminLoginPayload) =>
  http.post<AdminLoginResponse, AdminLoginResponse>('/admin/login', payload)

export const logout = () => http.get<void, void>('/admin/logout')

export const getProfile = () => http.get<AdminUser, AdminUser>('/admin/profile')

export const health = () => http.get<Record<string, unknown>, Record<string, unknown>>('/admin/health')
