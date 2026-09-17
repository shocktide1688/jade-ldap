const TOKEN_KEY = 'jade_access_token'
const AUTO_LOGIN_KEY = 'jade_auto_login'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export interface AutoLoginCredential {
  username: string
  password: string
}

function toBase64(text: string): string {
  const bytes = new TextEncoder().encode(text)
  let binary = ''
  for (const b of bytes) binary += String.fromCharCode(b)
  return btoa(binary)
}

function fromBase64(base64: string): string {
  const binary = atob(base64)
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/**
 * 自动登录凭证（仅做 Base64 混淆，不是加密——内网管理系统可接受的折衷）。
 * 勾选「自动登录」时保存，会话过期时用于静默重登。
 */
export function getAutoLogin(): AutoLoginCredential | null {
  const raw = localStorage.getItem(AUTO_LOGIN_KEY)
  if (!raw) return null
  try {
    const { u, p } = JSON.parse(fromBase64(raw))
    if (typeof u === 'string' && typeof p === 'string' && u && p) {
      return { username: u, password: p }
    }
    return null
  } catch {
    return null
  }
}

export function setAutoLogin(cred: AutoLoginCredential): void {
  localStorage.setItem(AUTO_LOGIN_KEY, toBase64(JSON.stringify({ u: cred.username, p: cred.password })))
}

export function clearAutoLogin(): void {
  localStorage.removeItem(AUTO_LOGIN_KEY)
}

export function hasAutoLogin(): boolean {
  return getAutoLogin() !== null
}
