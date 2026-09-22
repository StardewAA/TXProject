import { defineStore } from 'pinia';
import $share from '@/sheep/platform/share';
import { clone, cloneDeep } from 'lodash-es';
import cart from './cart';
import app from './app';
import { showAuthModal } from '@/sheep/hooks/useModal';
import UserApi from '@/sheep/api/member/user';
import PayWalletApi from '@/sheep/api/pay/wallet';
import OrderApi from '@/sheep/api/trade/order';
import CouponApi from '@/sheep/api/promotion/coupon';
import { recordTenantSession, recordPlatformSession, captureTenantOperation, isTenantOperationCurrent, ensureAccountSession, assertAccountProfile } from '@/sheep/helper/tenant-runtime';
import { readTenantToken, writeTenantToken } from '@/sheep/helper/tenant-runtime';

// 默认用户信息
const defaultUserInfo = {
  avatar: '', // 头像
  nickname: '', // 昵称
  gender: 0, // 性别
  mobile: '', // 手机号
  point: 0, // 积分
};

// 默认钱包信息
const defaultUserWallet = {
  balance: 0, // 余额
};

// 默认订单、优惠券等其他资产信息
const defaultNumData = {
  unusedCouponCount: 0,
  orderCount: {
    allCount: 0,
    unpaidCount: 0,
    undeliveredCount: 0,
    deliveredCount: 0,
    uncommentedCount: 0,
    afterSaleCount: 0,
  },
};

const user = defineStore('user', {
  state: () => ({
    userInfo: clone(defaultUserInfo), // 用户信息
    userWallet: clone(defaultUserWallet), // 用户钱包信息
    isLogin: !!readTenantToken('token'), // 登录状态
    numData: cloneDeep(defaultNumData), // 用户其他数据
    lastUpdateTime: 0, // 上次更新时间
    sessionError: '',
  }),

  actions: {
    // Tokens remain in their dedicated storage; cached private data never establishes identity.
    clearAccountData() {
      this.sessionError = '';
      this.isLogin = !!readTenantToken('token');
      this.userInfo = clone(defaultUserInfo);
      this.userWallet = clone(defaultUserWallet);
      this.numData = cloneDeep(defaultNumData);
      this.lastUpdateTime = 0;
      cart().emptyList();
    },
    async retrySession() {
      const operation = captureTenantOperation();
      try {
        await ensureAccountSession();
        if (!isTenantOperationCurrent(operation)) return;
        this.sessionError = '';
        this.lastUpdateTime = 0;
        await this.loginAfter();
      } catch (error) {
        if (isTenantOperationCurrent(operation)) this.sessionError = error.message || '登录状态暂无法核验，请重试';
      }
    },
    // 获取用户信息
    async getInfo() {
      const operation = captureTenantOperation();
      try {
        const response = await UserApi.getUserInfo();
        if (response?.code !== 0 || !response.data) {
          return;
        }
        if (!isTenantOperationCurrent(operation)) return;
        assertAccountProfile(response.data);
        this.userInfo = response.data;
        return Promise.resolve(response.data);
      } catch (error) {
        return;
      }
    },

    // 获得用户钱包
    async getWallet() {
      const operation = captureTenantOperation();
      try {
        const response = await PayWalletApi.getPayWallet();
        if (response?.code !== 0 || !response.data) {
          return;
        }
        if (isTenantOperationCurrent(operation)) this.userWallet = response.data;
      } catch (error) {
        return;
      }
    },

    // 获取订单、优惠券等其他资产信息
    getNumData() {
      const operation = captureTenantOperation();
      OrderApi.getOrderCount().then((res) => {
        if (isTenantOperationCurrent(operation) && res?.code === 0 && res.data) {
          this.numData.orderCount = res.data;
        }
      }).catch(() => {});
      CouponApi.getUnusedCouponCount().then((res) => {
        if (isTenantOperationCurrent(operation) && res?.code === 0) {
          this.numData.unusedCouponCount = res.data;
        }
      }).catch(() => {});
    },

    // 设置 token
    setToken(token = '', refreshToken = '', identity, refreshing = false) {
      this.sessionError = '';
      if (token && !refreshing) {
        this.userInfo = clone(defaultUserInfo);
        this.userWallet = clone(defaultUserWallet);
        this.numData = cloneDeep(defaultNumData);
        this.lastUpdateTime = 0;
        cart().emptyList();
      }
      recordTenantSession(token, identity, refreshing);
      if (token === '') {
        this.isLogin = false;
        writeTenantToken('token', '');
        writeTenantToken('refresh-token', '');
      } else {
        this.isLogin = true;
        writeTenantToken('token', token);
        writeTenantToken('refresh-token', refreshToken);
        if (!refreshing) this.loginAfter();
      }
      return this.isLogin;
    },

    // Platform tokens are still legacy member JWTs, but their tenant is only the
    // trusted auth carrier. Do not start tenant-owned profile/cart reads here.
    setPlatformToken(token = '', refreshToken = '', identity, refreshing = false) {
      this.sessionError = '';
      if (!token) return this.setToken();
      if (!refreshing) {
        this.userInfo = clone(defaultUserInfo);
        this.userWallet = clone(defaultUserWallet);
        this.numData = cloneDeep(defaultNumData);
        this.lastUpdateTime = 0;
        cart().emptyList();
      }
      recordPlatformSession(token, identity, refreshing);
      writeTenantToken('token', token);
      writeTenantToken('refresh-token', refreshToken);
      this.isLogin = true;
      return true;
    },

    // 更新用户相关信息 (手动限流，5 秒之内不刷新)
    async updateUserData() {
      const operation = captureTenantOperation();
      if (!this.isLogin) {
        this.resetUserData();
        return;
      }
      // 防抖，5 秒之内不刷新
      const nowTime = new Date().getTime();
      if (this.lastUpdateTime + 5000 > nowTime) {
        return;
      }
      this.lastUpdateTime = nowTime;

      // 获取最新信息
      const info = await this.getInfo();
      if (!isTenantOperationCurrent(operation)) return;
      if (!info) { this.lastUpdateTime = 0; return; }
      this.getWallet();
      this.getNumData();
      return this.userInfo;
    },

    // 重置用户默认数据
    resetUserData() {
      // 清空 token
      this.setToken();
      // 清空用户相关的缓存
      this.userInfo = clone(defaultUserInfo);
      this.userWallet = clone(defaultUserWallet);
      this.numData = cloneDeep(defaultNumData);
      this.lastUpdateTime = 0;
      // 清空购物车的缓存
      cart().emptyList();
    },

    // 登录后，加载各种信息
    async loginAfter() {
      const operation = captureTenantOperation();
      const info = await this.updateUserData();
      if (!isTenantOperationCurrent(operation)) return;
      if (!info) return;

      // 加载购物车
      cart().getList();
      // 登录后设置全局分享参数
      $share.getShareInfo();

      // 提醒绑定手机号
      if (app().platform.bind_mobile && !this.userInfo.mobile && !this.userInfo.loginName) {
        showAuthModal('changeMobile');
      }

      // 绑定推广员
      $share.bindBrokerageUser();
    },

    // 登出系统
    async logout() {
      this.resetUserData();
      return !this.isLogin;
    },
  },
  // Profile/wallet/counters are server-derived private data: never rehydrate them before proof.
});

export default user;
