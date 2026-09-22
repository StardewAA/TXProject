import request from '@/sheep/request';

const platformPrivate = {
  scope: 'platform',
  auth: true,
  showLoading: false,
};

const PlatformCartApi = {
  getList: () => request({
    url: '/trade/platform-cart/list',
    method: 'GET',
    custom: platformPrivate,
  }),
  add: (skuId, quantity) => request({
    url: '/trade/platform-cart/add',
    method: 'POST',
    data: { skuId, quantity },
    custom: { ...platformPrivate, showSuccess: true, successMsg: '已加入平台购物车' },
  }),
  updateCount: (id, quantity) => request({
    url: '/trade/platform-cart/update-count',
    method: 'PUT',
    data: { id, quantity },
    custom: platformPrivate,
  }),
  updateSelected: (ids, selected) => request({
    url: '/trade/platform-cart/update-selected',
    method: 'PUT',
    data: { ids, selected },
    custom: platformPrivate,
  }),
  delete: (ids) => request({
    url: '/trade/platform-cart/delete',
    method: 'DELETE',
    params: { ids: ids.join(',') },
    custom: platformPrivate,
  }),
};

export default PlatformCartApi;
