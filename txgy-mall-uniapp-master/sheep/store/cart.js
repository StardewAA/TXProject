import { defineStore } from 'pinia';
import CartApi from '@/sheep/api/trade/cart';
import { captureTenantOperation, isTenantOperationCurrent } from '@/sheep/helper/tenant-runtime';

// Snapshots contain credentials: keep them outside reactive/persistable state.
const runtimes = new WeakMap();
function runtime(store) {
  if (!runtimes.has(store)) runtimes.set(store, { owner: undefined, read: 0, write: 0 });
  return runtimes.get(store);
}
const rows = value => value.filter(item => item && typeof item === 'object' && item.id != null);
const validCount = value => Number.isSafeInteger(value) && value > 0 && value <= 2147483647;
const positiveId = value => typeof value === 'number' ? Number.isSafeInteger(value) && value > 0
  : typeof value === 'string' && /^[1-9]\d{0,18}$/.test(value);
function purchaseReady(item) {
  const count = Number(item.count), price = item.sku?.price;
  return positiveId(item.id) && positiveId(item.sku?.id) && positiveId(item.spu?.id)
    && validCount(count) && Number.isSafeInteger(price) && price >= 0
    && Number.isSafeInteger(count * price);
}

const cart = defineStore('cart', {
  state: () => ({
    list: [], newList: [], selectedIds: [], isAllSelected: false, totalPriceSelected: 0,
    editMode: false, loading: false, loaded: false, busy: false,
    loadError: '', operationError: '', revision: 0,
  }),
  actions: {
    bindSession() {
      const operation = captureTenantOperation(), current = runtime(this);
      if (current.owner !== undefined && !isTenantOperationCurrent(current.owner)) this.emptyList();
      current.owner = operation;
      return operation;
    },
    deriveSelection() {
      const selectable = this.editMode ? this.list : this.newList;
      this.selectedIds = this.editMode
        ? this.selectedIds.filter(id => selectable.some(item => item.id === id))
        : selectable.filter(item => item.selected).map(item => item.id);
      this.isAllSelected = selectable.length > 0 && selectable.every(item => this.selectedIds.includes(item.id));
      // Invalid rows may be selected for deletion, never for financial totals.
      this.totalPriceSelected = this.newList.filter(item => this.selectedIds.includes(item.id))
        .reduce((total, item) => total + Number(item.count) * Number(item.sku?.price), 0);
      if (!Number.isSafeInteger(this.totalPriceSelected) || this.totalPriceSelected < 0) {
        this.totalPriceSelected = 0;
        this.loadError = '商品金额暂无法核验，请刷新购物车';
      }
    },
    async getList(duringMutation = false) {
      const operation = this.bindSession();
      if (this.busy && !duringMutation) return false;
      const current = runtime(this), sequence = ++current.read;
      const active = () => current.read === sequence && isTenantOperationCurrent(operation);
      this.loading = true;
      try {
        const response = await CartApi.getCartList();
        if (!active()) return false;
        if (response?.code !== 0 || !Array.isArray(response.data?.validList)
            || !Array.isArray(response.data?.invalidList)) throw new Error('CART_UNAVAILABLE');
        const advertised = rows(response.data.validList);
        const valid = advertised.filter(purchaseReady);
        const invalid = [...advertised.filter(item => !purchaseReady(item)), ...rows(response.data.invalidList)];
        this.list = [...valid, ...invalid]; this.newList = valid; this.loaded = true;
        this.loadError = ''; this.operationError = '';
        this.deriveSelection(); this.revision++;
        return !this.loadError;
      } catch {
        if (active()) this.loadError = '购物车加载失败，暂不能操作，请点击重试';
        // A failed read is not an empty cart; retain the last confirmed rows.
        return false;
      } finally { if (active()) this.loading = false; }
    },
    canOperate() {
      const owner = runtime(this).owner;
      return owner !== undefined && isTenantOperationCurrent(owner) && this.loaded
        && !this.loading && !this.busy && !this.loadError && !this.operationError;
    },
    onChangeEditMode(flag) {
      if (!this.canOperate()) return false;
      this.editMode = !!flag; this.selectedIds = []; this.deriveSelection();
      return true;
    },
    async mutate(send, operation = captureTenantOperation()) {
      if (!isTenantOperationCurrent(operation)) return false;
      this.bindSession();
      if (this.busy || this.loadError || this.operationError) return false;
      const current = runtime(this), sequence = ++current.write;
      ++current.read; // An earlier GET cannot overwrite a post-write refresh.
      const active = () => current.write === sequence && isTenantOperationCurrent(operation);
      this.busy = true; this.loading = false; this.operationError = '';
      try {
        const response = await send();
        if (!active()) return false;
        if (response?.code !== 0) throw new Error('CART_WRITE_FAILED');
        const refreshed = await this.getList(true);
        // The write is confirmed even if its refresh fails. Never invite a duplicate add.
        // loadError still blocks any subsequent write/checkout until reconciliation.
        if (active() && !refreshed) this.loadError = '购物车已更新，但列表加载失败，请点击重试核对，无需重复操作';
        return active();
      } catch {
        if (active()) this.operationError = '购物车操作未确认成功，请刷新后核对';
        return false;
      } finally { if (active()) { this.busy = false; this.revision++; } }
    },
    async add(goodsInfo) {
      const count = Number(goodsInfo?.goods_num ?? 1);
      if (!positiveId(goodsInfo?.id) || !validCount(count)) return false;
      return this.mutate(() => CartApi.addCart({ skuId: goodsInfo.id, count }));
    },
    async update(goodsInfo) {
      const count = Number(goodsInfo?.goods_num);
      if (!this.canOperate() || !validCount(count)
          || !this.newList.some(item => item.id === goodsInfo.goods_id)) return false;
      return this.mutate(() => CartApi.updateCartCount({ id: goodsInfo.goods_id, count }));
    },
    async delete(ids, operation = captureTenantOperation()) {
      const selected = Array.isArray(ids) ? [...ids] : [ids];
      if (!isTenantOperationCurrent(operation) || !this.canOperate() || !selected.length
          || !selected.every(id => this.list.some(item => item.id === id))) return false;
      return this.mutate(() => CartApi.deleteCart(selected.join(',')), operation);
    },
    async selectSingle(id) {
      if (!this.canOperate()) return false;
      const selectable = this.editMode ? this.list : this.newList;
      if (!selectable.some(item => item.id === id)) return false;
      if (this.editMode) {
        this.selectedIds = this.selectedIds.includes(id) ? this.selectedIds.filter(value => value !== id) : [...this.selectedIds, id];
        this.deriveSelection(); return true;
      }
      const selected = !this.selectedIds.includes(id);
      return this.mutate(() => CartApi.updateCartSelected({ ids: [id], selected }));
    },
    async selectAll(flag) {
      if (!this.canOperate()) return false;
      if (this.editMode) {
        this.selectedIds = flag ? this.list.map(item => item.id) : [];
        this.deriveSelection(); return true;
      }
      const ids = this.newList.map(item => item.id);
      if (!ids.length) return false;
      return this.mutate(() => CartApi.updateCartSelected({ ids, selected: !!flag }));
    },
    emptyList() {
      const current = runtime(this);
      ++current.read; ++current.write; current.owner = undefined;
      this.list = []; this.newList = []; this.selectedIds = [];
      this.isAllSelected = false; this.totalPriceSelected = 0; this.editMode = false;
      this.loading = false; this.loaded = false; this.busy = false;
      this.loadError = ''; this.operationError = ''; this.revision++;
    },
  },
});

export default cart;
