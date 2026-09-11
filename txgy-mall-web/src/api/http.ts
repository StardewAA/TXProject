import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

interface ApiEnvelope<T> {
  code: number
  data: T
  msg?: string
}

interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

const ACCESS_TOKEN_KEY = 'mall-pc-access-token'
const REFRESH_TOKEN_KEY = 'mall-pc-refresh-token'
const TENANT_ID = import.meta.env.VITE_TENANT_ID || '1'
const API_BASE = `${import.meta.env.VITE_API_BASE_URL || ''}${import.meta.env.VITE_API_PREFIX || '/app-api'}`

export const tokenStorage = {
  getAccess: () => localStorage.getItem(ACCESS_TOKEN_KEY) || '',
  getRefresh: () => localStorage.getItem(REFRESH_TOKEN_KEY) || '',
  set(accessToken: string, refreshToken: string) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    window.dispatchEvent(new CustomEvent('mall:token-updated'))
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    window.dispatchEvent(new CustomEvent('mall:token-updated'))
  },
}

function authorizationValue(token: string): string {
  return token.startsWith('Bearer ') ? token : `Bearer ${token}`
}

const client = axios.create({
  baseURL: API_BASE,
  timeout: 12000,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json;charset=UTF-8',
  },
})

client.interceptors.request.use((config) => {
  config.headers['tenant-id'] = TENANT_ID
  config.headers.terminal = '20'
  const token = tokenStorage.getAccess()
  if (token) config.headers.Authorization = authorizationValue(token)
  return config
})

let refreshing: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  if (refreshing) return refreshing
  const refreshToken = tokenStorage.getRefresh()
  if (!refreshToken) throw new Error('登录状态已失效')

  refreshing = axios
    .post<ApiEnvelope<{ accessToken: string; refreshToken: string }>>(
      `${API_BASE}/member/auth/refresh-token`,
      undefined,
      {
        params: { refreshToken },
        headers: { 'tenant-id': TENANT_ID, terminal: '20' },
        timeout: 12000,
      },
    )
    .then(({ data }) => {
      if (data.code !== 0 || !data.data?.accessToken) throw new Error(data.msg || '登录状态已失效')
      tokenStorage.set(data.data.accessToken, data.data.refreshToken)
      return data.data.accessToken
    })
    .finally(() => {
      refreshing = null
    })

  return refreshing
}

client.interceptors.response.use(
  async (response) => {
    const envelope = response.data as ApiEnvelope<unknown>
    if (!envelope || typeof envelope !== 'object' || typeof envelope.code !== 'number') {
      throw new Error('服务器返回了无法识别的数据')
    }
    if (envelope.code === 0) return response

    const original = response.config as RetryConfig
    if (envelope.code === 401 && !original._retry && !original.url?.includes('/member/auth/refresh-token')) {
      original._retry = true
      try {
        const accessToken = await refreshAccessToken()
        original.headers.Authorization = authorizationValue(accessToken)
        return client(original)
      } catch {
        tokenStorage.clear()
        window.dispatchEvent(new CustomEvent('mall:unauthorized'))
      }
    }
    throw new Error(envelope.msg || '请求失败')
  },
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const message =
      error.code === 'ECONNABORTED'
        ? '请求超时，请检查商城服务是否已启动'
        : error.response?.data?.msg || error.message || '网络请求失败'
    return Promise.reject(new Error(message))
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await client.request<ApiEnvelope<T>>(config)
    return response.data.data
  } catch (error) {
    const message = error instanceof Error ? error.message : '请求失败'
    if (config.headers?.['x-silent-error'] !== true) ElMessage.error(message)
    throw error
  }
}
