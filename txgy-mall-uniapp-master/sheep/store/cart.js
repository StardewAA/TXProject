import { defineStore } from 'pinia';
import CartApi from '@/sheep/api/trade/cart';

const cart = defineStore('cart', {
  state: () => ({
    list: [], // 购物车列表（invalidList + validList）
    selectedIds: [], // 已选列表
    isAllSelected: false, // 是否全选
    totalPriceSelected: 0, // 选中项总金额
    newList: [], // 除去已下架的购物车列表（validList）
    editMode: false, // 是否是编辑模式
  }),
  actions: {
    // 获取购物车列表
    async getList() {
      try {
        const response = await CartApi.getCartList();
        if (!response || response.code !== 0) {
          return;
        }
        const data = response.data || {};
        const validList = Array.isArray(data.validList)
          ? data.validList.filter((item) => item && typeof item === 'object' && item.id !== undefined)
          : [];
        const invalidList = Array.isArray(data.invalidList)
          ? data.invalidList.filter((item) => item && typeof item === 'object' && item.id !== undefined)
          : [];
        this.list = [...validList, ...invalidList];
        this.newList = validList;

        // 计算各种关联属性
        this.selectedIds = [];
        this.isAllSelected = true;
        this.totalPriceSelected = 0;
        (this.editMode ? this.list : this.newList).forEach((item) => {
          if (item.selected) {
            this.selectedIds.push(item.id);
            this.totalPriceSelected += Number(item.count || 0) * Number(item.sku?.price || 0);
          } else {
            this.isAllSelected = false;
          }
        });
      } catch (error) {
        // 未登录或接口暂不可用时保持空购物车，不阻塞首页/分类页渲染。
        this.list = [];
        this.newList = [];
        this.selectedIds = [];
        this.isAllSelected = true;
        this.totalPriceSelected = 0;
      }
    },

    onChangeEditMode(flag) {
      this.editMode = flag;
      this.selectedIds = [];
      this.getList();
    },

    // 添加购物车
    async add(goodsInfo) {
      try {
        // 添加购物项
        const response = await CartApi.addCart({
          skuId: goodsInfo.id,
          count: goodsInfo.goods_num || 1,
        });
        // 刷新购物车列表
        if (response?.code !== 0) return false;
        await this.getList();
      } catch (error) {
        return false;
      }
      return true;
    },

    // 更新购物车
    async update(goodsInfo) {
      try {
        const response = await CartApi.updateCartCount({
          id: goodsInfo.goods_id,
          count: goodsInfo.goods_num,
        });
        if (response?.code !== 0) return false;
        await this.getList();
      } catch (error) {
        return false;
      }
      return true;
    },

    // 移除购物车
    async delete(ids) {
      let idsTemp = '';
      if (Array.isArray(ids)) {
        idsTemp = ids.join(',');
      } else {
        idsTemp = ids;
      }
      try {
        const response = await CartApi.deleteCart(idsTemp);
        if (response?.code !== 0) return false;
        await this.getList();
      } catch (error) {
        return false;
      }
      return true;
    },

    // 单选购物车商品
    async selectSingle(goodsId) {
      try {
        const response = await CartApi.updateCartSelected({
          ids: [goodsId],
          selected: !this.selectedIds.includes(goodsId), // 取反
        });
        if (response?.code !== 0) return false;
        await this.getList();
      } catch (error) {
        return false;
      }
      return true;
    },

    // 全选购物车商品
    async selectAll(flag) {
      try {
        const response = await CartApi.updateCartSelected({
          ids: this.list.map((item) => item.id),
          selected: flag,
        });
        if (response?.code !== 0) return false;
        await this.getList();
      } catch (error) {
        return false;
      }
      return true;
    },

    // 清空购物车。注意，仅用于用户退出时，重置数据
    emptyList() {
      this.list = [];
      this.selectedIds = [];
      this.isAllSelected = true;
      this.totalPriceSelected = 0;
    },
  },
  persist: {
    enabled: true,
    strategies: [
      {
        key: 'cart-store',
      },
    ],
  },
});

export default cart;
