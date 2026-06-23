const ACCESS_TOKEN_KEY = 'hiveapp.admin.accessToken'
const REFRESH_TOKEN_KEY = 'hiveapp.admin.refreshToken'

export type AdminTokens = {
  accessToken: string
  refreshToken: string
}

export function getAdminAccessToken() {
  return sessionStorage.getItem(ACCESS_TOKEN_KEY)
}

export function setAdminTokens(tokens: AdminTokens) {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
  sessionStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
}

export function clearAdminTokens() {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  sessionStorage.removeItem(REFRESH_TOKEN_KEY)
}
