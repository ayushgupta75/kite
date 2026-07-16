export const API_BASE = import.meta.env.VITE_API_BASE_URL

async function apiFetch(path, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    credentials: 'include',
    headers: options.body
      ? { 'Content-Type': 'application/json', ...options.headers }
      : options.headers,
  })

  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new Error(body || res.statusText)
  }

  const text = await res.text()
  return text ? JSON.parse(text) : null
}

export const signup = (email, password) =>
  apiFetch('/auth/signup', { method: 'POST', body: JSON.stringify({ email, password }) })

export const login = (email, password) =>
  apiFetch('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })

export const logout = () => apiFetch('/auth/logout', { method: 'POST' })

export const getSessionStatus = () => apiFetch('/auth/session')
