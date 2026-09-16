import axios from 'axios';

const requestClient = axios.create({
  baseURL: '/admin-api',
  timeout: 30000,
});

requestClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

requestClient.interceptors.response.use(
  (response) => {
    const data = response.data;
    if (data && data.code !== 0) {
      return Promise.reject(new Error(data.msg || '请求失败'));
    }
    return data?.data ?? data;
  },
  (error) => Promise.reject(error),
);

export { requestClient };
