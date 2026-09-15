import axios from 'axios';

const appClient = axios.create({
  baseURL: '/app-api',
  timeout: 30000,
});

appClient.interceptors.response.use(
  (response) => {
    const data = response.data;
    if (data && data.code !== 0) {
      return Promise.reject(new Error(data.msg || '请求失败'));
    }
    return data?.data ?? data;
  },
  (error) => Promise.reject(error),
);

export function searchCmsContent(params) {
  return appClient.get('/cms/search', { params });
}

export function getCmsContentDetail(type, id) {
  return appClient.get(`/cms/${type}/${id}`);
}
