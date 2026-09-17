import axios from 'axios';

const authClient = axios.create({
  baseURL: '/admin-api',
  timeout: 30000,
  headers: {
    'tenant-id': '1',
  },
});

authClient.interceptors.response.use(
  (response) => {
    const data = response.data;
    if (data && data.code !== 0) {
      return Promise.reject(new Error(data.msg || '请求失败'));
    }
    return data?.data ?? data;
  },
  (error) => Promise.reject(error),
);

export function loginApi(params) {
  return authClient.post('/system/auth/login', {
    ...params,
    loginType: 'personal',
  });
}

export function registerApi(params) {
  return authClient.post('/system/auth/register', {
    ...params,
    registerType: 'personal',
  });
}
