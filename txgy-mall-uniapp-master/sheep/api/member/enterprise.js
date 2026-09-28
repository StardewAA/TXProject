import request from '@/sheep/request';
export default {
  mine: () => request({ url: '/product/enterprise-membership/mine', custom: { auth: true } }),
  apply: data => request({ url: '/product/enterprise-membership/apply', method: 'POST', data, custom: { auth: true } }),
};
