export const comparisonDefault = () => ({ version: 0, skuIds: [], quantity: 1, preference: 'PRICE' });
export const comparisonPreferences = ['PRICE', 'DELIVERY', 'BALANCED'];
export function comparisonList(value) {
  if (!value || !Array.isArray(value.skuIds) || value.skuIds.length > 4
    || value.skuIds.some(id => !Number.isSafeInteger(id) || id <= 0)
    || new Set(value.skuIds).size !== value.skuIds.length
    || !Number.isSafeInteger(value.version) || value.version < 0
    || !Number.isInteger(value.quantity) || value.quantity < 1 || value.quantity > 999
    || !comparisonPreferences.includes(value.preference)) throw new Error('对比清单格式无效，请刷新后重试');
  return { version: value.version, skuIds: [...value.skuIds], quantity: value.quantity, preference: value.preference };
}
export function mergeComparisonLists(saved, guest) {
  const left = comparisonList(saved), right = comparisonList(guest);
  const skuIds = [...new Set([...left.skuIds, ...right.skuIds])];
  if (skuIds.length > 4) throw new Error('合并后超过 4 个规格，请先移除部分商品或放弃访客清单');
  return { ...left, skuIds };
}
export function comparisonMoney(value) {
  return Number.isSafeInteger(value) && value >= 0 ? `￥${(value / 100).toFixed(2)}` : '待确认';
}
export function comparisonResult(value, list) {
  if (!value || value.quantityUnit !== 'SALE_UNIT' || value.quantity !== list.quantity || value.preference !== list.preference
    || !Array.isArray(value.items) || value.items.length !== list.skuIds.length
    || value.items.some((item, index) => !item || item.skuId !== list.skuIds[index])) {
    throw new Error('对比结果与当前清单不一致，请刷新');
  }
  return value;
}
export function comparisonQuote(value, list, addressId) {
  if (!value || value.priceBasis !== 'CHECKOUT_ESTIMATE' || value.quantity !== list.quantity
    || (value.addressId || null) !== (addressId || null) || !Array.isArray(value.items)
    || value.items.length !== list.skuIds.length
    || value.items.some((item, i) => !item || item.skuId !== list.skuIds[i] || item.quantity !== list.quantity)) {
    throw new Error('报价与当前清单不一致，请重新估价');
  }
  return value;
}
// Preserve duplicate attribute names as original data, never normalize unlike units or infer equality.
export function comparisonRows(items, differencesOnly = false) {
  const names = [...new Set(items.flatMap(item => item.available && Array.isArray(item.properties)
    ? item.properties.map(prop => String(prop.name || '')).filter(Boolean) : []))];
  return names.map(name => {
    const values = items.map(item => item.available && Array.isArray(item.properties)
      ? item.properties.filter(prop => prop.name === name).map(prop => String(prop.value || '待补充')).join(' / ') || '待补充'
      : '不可用');
    return { name, values, different: new Set(values).size > 1 };
  }).filter(row => !differencesOnly || row.different);
}
export function createComparisonController(deps, state) {
  let generation = 0, owner;
  const reset = () => { generation++; owner = undefined; Object.assign(state, {
    list: comparisonDefault(), guest: comparisonDefault(), ready: false, loading: false,
    saving: false, authenticated: false, error: '',
  }); };
  reset();
  const alive = (scope, revision) => revision === generation && deps.current(scope);
  const guest = scope => {
    try { return comparisonList(deps.readGuest(scope) || comparisonDefault()); }
    catch { return comparisonDefault(); }
  };
  async function load() {
    if (state.saving) return false;
    const scope = deps.capture(), revision = ++generation;
    owner = scope;
    Object.assign(state, { list: comparisonDefault(), ready: false, loading: true, error: '',
      authenticated: scope.authenticated, guest: guest(scope) });
    try {
      const list = scope.authenticated ? await deps.readList() : guest(scope);
      if (!alive(scope, revision)) return false;
      state.list = comparisonList(list); state.ready = true;
      return true;
    } catch (error) {
      if (alive(scope, revision)) state.error = error?.message || '对比清单加载失败，请重试';
      return false;
    } finally { if (alive(scope, revision)) state.loading = false; }
  }
  async function save(next) {
    if (!owner || !state.ready || !deps.current(owner)) throw new Error('请先刷新当前账号的对比清单');
    if (state.saving || state.loading) throw new Error('正在处理，请稍候');
    const payload = comparisonList({ ...next, version: state.list.version });
    const scope = owner, revision = ++generation;
    state.saving = true; state.error = '';
    try {
      let saved;
      if (scope.authenticated) saved = await deps.saveList(payload);
      else { deps.writeGuest(scope, payload); saved = payload; }
      if (!alive(scope, revision)) return false;
      state.list = comparisonList(saved); state.guest = guest(scope);
      return true;
    } catch (error) {
      if (alive(scope, revision)) {
        state.ready = false;
        state.error = error?.message || '保存失败，请刷新后重试';
      }
      return false;
    } finally { if (alive(scope, revision)) state.saving = false; }
  }
  async function add(skuId) {
    if (!state.ready && !await load()) return false;
    if (state.list.skuIds.includes(skuId)) return true;
    if (state.list.skuIds.length >= 4) throw new Error('最多对比 4 个规格，请先移除部分商品');
    return save({ ...state.list, skuIds: [...state.list.skuIds, skuId] });
  }
  async function merge() {
    if (!owner?.authenticated || !state.ready) throw new Error('请登录并刷新清单后再合并');
    const scope = owner;
    const capturedGuest = guest(scope);
    const saved = await save(mergeComparisonLists(state.list, capturedGuest));
    if (saved && deps.current(scope)) {
      const currentGuest = guest(scope);
      if (JSON.stringify(currentGuest) === JSON.stringify(capturedGuest)) {
        deps.writeGuest(scope, comparisonDefault()); state.guest = comparisonDefault();
      } else state.guest = currentGuest;
    }
    return saved;
  }
  function discardGuest() {
    const scope = deps.capture();
    if (!scope.authenticated) throw new Error('当前为访客清单，请使用移除功能');
    deps.writeGuest(scope, comparisonDefault()); state.guest = comparisonDefault();
  }
  return { reset, load, save, add, merge, discardGuest };
}
