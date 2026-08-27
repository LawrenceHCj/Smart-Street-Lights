export type PlatformRole = 'admin' | 'municipal' | 'operator'

function isPlatformRole(value: unknown): value is PlatformRole {
  return value === 'admin' || value === 'municipal' || value === 'operator'
}

function roleFromToken(): PlatformRole | null {
  const token = localStorage.getItem('token')
  if (!token) return null
  try {
    const payloadPart = token.split('.')[1]
    if (!payloadPart) return null
    const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/')
    const normalized = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    const payload = JSON.parse(atob(normalized)) as { role?: unknown }
    return isPlatformRole(payload.role) ? payload.role : null
  } catch {
    return null
  }
}

export function getCurrentRole(): PlatformRole | null {
  const stored = localStorage.getItem('role')
  if (isPlatformRole(stored)) return stored
  const tokenRole = roleFromToken()
  if (tokenRole) localStorage.setItem('role', tokenRole)
  return tokenRole
}

export function isCurrentUserAdmin(): boolean {
  return getCurrentRole() === 'admin'
}

export function getCurrentUsername(): string | null {
  const username = localStorage.getItem('username')?.trim()
  return username || null
}

export function clearSession(): void {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('role')
}
