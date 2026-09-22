import request from '@/sheep/request';

const InquiryApi = {
  getPublicPage: (params) => request({
    url: '/product/purchase-inquiry/public-page',
    method: 'GET',
    params,
    custom: { isToken: false, showLoading: false, showError: false },
  }),
  getPublicDetail: (id) => request({
    url: '/product/purchase-inquiry/public-get',
    method: 'GET',
    params: { id },
    custom: { isToken: false, showLoading: false, showError: false },
  }),
};

export default InquiryApi;
