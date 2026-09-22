/** Read-only customer display; never use this result as enterprise authorization. */
export function normalizeCustomerAffiliation(value) {
  const states = ['DISABLED', 'NONE', 'ACTIVE', 'OWNER_UNAVAILABLE', 'TEMPORARILY_UNAVAILABLE'];
  if (!value || !states.includes(value.state)) throw new Error('Invalid customer affiliation');
  if (value.state === 'ACTIVE' && (typeof value.serviceEnterpriseName !== 'string' || !value.serviceEnterpriseName.trim())) {
    throw new Error('Missing service enterprise');
  }
  return { state: value.state, serviceEnterpriseName: value.state === 'ACTIVE' ? value.serviceEnterpriseName.trim() : null };
}

export function createCustomerAffiliationLoader(state, getScope, fetchProfile) {
  let generation = 0;
  function reset() {
    generation++;
    Object.assign(state, { result: null, loading: false, error: false, scope: '' });
  }
  async function refresh() {
    const scope = getScope();
    reset();
    if (!scope) return false;
    const version = generation;
    Object.assign(state, { scope, loading: true });
    const current = () => version === generation && getScope() === scope;
    try {
      const result = normalizeCustomerAffiliation(await fetchProfile());
      if (!current()) return false;
      state.result = result;
      return true;
    } catch {
      if (current()) state.error = true;
      return false;
    } finally {
      if (current()) state.loading = false;
    }
  }
  return { reset, refresh };
}

export function customerAffiliationMessage(state) {
  if (state.loading) return '正在读取客户服务归属…';
  if (state.error || state.result?.state === 'TEMPORARILY_UNAVAILABLE') return '服务企业信息暂时无法查询，请重试；不代表企业资质已失效。';
  if (state.result?.state === 'ACTIVE') return state.result.serviceEnterpriseName;
  if (state.result?.state === 'OWNER_UNAVAILABLE') return '原服务企业当前不可用，客户归属未被自动变更。';
  if (state.result?.state === 'NONE') return '当前账号尚未关联客户服务企业。';
  return '';
}
