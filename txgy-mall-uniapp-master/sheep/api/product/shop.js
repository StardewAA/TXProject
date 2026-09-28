import request from '@/sheep/request';

export default {
  getList: () => request({ url: '/product/shop/list', method: 'GET', custom: { showLoading: false, showError: false, scope: 'browse' } }),
  get: (id) => request({ url: '/product/shop/get', method: 'GET', params: { id }, custom: { showLoading: false, showError: false, scope: 'browse' } }),
};
