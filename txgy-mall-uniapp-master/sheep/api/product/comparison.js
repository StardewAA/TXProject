import request from '@/sheep/request';
const call = (url, method, data, auth = false) => request({ url, method, data,
  custom: { auth, showLoading: false, showError: false } });
export default {
  getList: () => call('/product/comparison/list', 'GET', undefined, true),
  saveList: data => call('/product/comparison/list', 'PUT', data, true),
  calculate: data => call('/product/comparison/calculate', 'POST', data),
  quote: data => call('/trade/product-comparison/quote', 'POST', data, true),
};
