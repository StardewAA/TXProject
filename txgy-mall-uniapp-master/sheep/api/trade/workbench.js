import request from '@/sheep/request';

const custom = { auth: true, showLoading: false, showError: false };
export default {
  summary: params => request({ url: '/trade/order-workbench/summary', method: 'GET', params: { ...params, role: 'BUYER' }, custom }),
  page: params => request({ url: '/trade/order-workbench/page', method: 'GET', params: { ...params, role: 'BUYER' }, custom }),
  read: eventIds => request({ url: '/trade/order-workbench/read', method: 'POST', data: { role: 'BUYER', eventIds }, custom }),
};
