import request from '@/sheep/request';

const platformPrivate = {
  scope: 'platform',
  auth: true,
  showLoading: false,
};

const PlatformCheckoutApi = {
  preview: (cartItemIds, addressId) => request({
    url: '/trade/platform-checkout/preview',
    method: 'POST',
    data: { cartItemIds, addressId },
    custom: platformPrivate,
  }),
  submit: (cartItemIds, addressId, idempotencyKey) => request({
    url: '/trade/platform-checkout/submit',
    method: 'POST',
    data: { cartItemIds, addressId, idempotencyKey },
    custom: platformPrivate,
  }),
  page: (pageNo, pageSize = 10) => request({
    url: '/trade/platform-checkout/page',
    method: 'GET',
    params: { pageNo, pageSize },
    custom: platformPrivate,
  }),
  get: (checkoutNo) => request({
    url: `/trade/platform-checkout/${encodeURIComponent(String(checkoutNo || '').trim())}`,
    method: 'GET',
    custom: platformPrivate,
  }),
};

export default PlatformCheckoutApi;
