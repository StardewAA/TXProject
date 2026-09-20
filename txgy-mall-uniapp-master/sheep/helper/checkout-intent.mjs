// Browser recovery protection, not a server idempotency or authorization protocol.
let intentSequence = 0;
export function checkoutIntentId() {
  // Native/non-secure runtimes: same non-authentication-key principle as existing chatUuid.
  if (!globalThis.crypto?.getRandomValues) return 'intent-' + Date.now().toString(16).padStart(12,'0').slice(-12)
    + (++intentSequence >>> 0).toString(16).padStart(8,'0')
    + Array.from({length:12},()=>Math.floor(Math.random()*16).toString(16)).join('');
  const bytes = new Uint8Array(16); globalThis.crypto.getRandomValues(bytes);
  return 'intent-' + Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
}
export function validCheckoutIntent(id) { return typeof id === 'string' && /^(intent-[a-f0-9]{32}|legacy-[a-f0-9]{32})$/.test(id); }
export function legacyCheckoutIntent(payload) {
  // Deterministic non-PII fingerprint: preserve quantities, cart lines and activity identities.
  const source = JSON.stringify({
    items: (Array.isArray(payload?.items) ? payload.items : []).map(item => [String(item?.skuId ?? ''), String(item?.count ?? ''), String(item?.cartId ?? '')]).sort((a,b) => JSON.stringify(a).localeCompare(JSON.stringify(b))),
    activity: ['combinationActivityId','combinationHeadId','seckillActivityId','pointActivityId'].map(key => String(payload?.[key] ?? '')),
  });
  const hashes = [2166136261, 2246822519, 3266489917, 668265263];
  for (let i=0;i<source.length;i++) for(let j=0;j<hashes.length;j++) hashes[j]=Math.imul((hashes[j]^source.charCodeAt(i))>>>0,16777619+j*2)>>>0;
  return 'legacy-'+hashes.map(value=>value.toString(16).padStart(8,'0')).join('');
}
export function openCheckoutIntent(storage, intentId, owner) {
  const tenantId=String(owner?.tenantId ?? ''), memberId=String(owner?.memberId ?? '');
  if (!validCheckoutIntent(intentId) || !/^[1-9]\d*$/.test(tenantId) || !/^[1-9]\d*$/.test(memberId))
    throw new Error('当前商城账号或结算标识未核验，请从购物车重新结算');
  const key='mall-checkout-intent:v1:'+intentId;
  const fail=()=>new Error('无法安全保存或核验结算状态，请勿重复提交；先查看订单列表');
  function read() {
    let raw; try { raw=storage.getItem(key); } catch { throw fail(); }
    if (raw===null || raw===undefined || raw==='') return null;
    let value; try { value=JSON.parse(raw); } catch { throw fail(); }
    if (!value || value.version!==1 || value.intentId!==intentId || !['DRAFT','PENDING','CREATED'].includes(value.state)) throw fail();
    if (value.tenantId!==tenantId || value.memberId!==memberId) throw new Error('此结算属于其他账号或商城，请从当前账号购物车重新采购');
    return value;
  }
  function write(value) {
    try { storage.setItem(key,JSON.stringify(value)); if(JSON.stringify(read())!==JSON.stringify(value)) throw fail(); }
    catch(error) { throw fail(); }
  }
  if(!read()) write({version:1,tenantId,memberId,intentId,state:'DRAFT'});
  return {
    intentId,
    state() { const value=read(); if(!value) throw fail(); return value.state; },
    begin() {
      const before=read(); if(!before || before.state!=='DRAFT') throw new Error('本次结算已提交或结果待核验，请先查看订单，不要重复提交');
      const attemptId=checkoutIntentId();
      write({...before,state:'PENDING',attemptId});
      const complete=state=>{
        const value=read();
        if(!value || value.attemptId!==attemptId || value.state!=='PENDING') return false;
        write({...value,state}); return true;
      };
      return { reject:()=>complete('DRAFT'), created:()=>complete('CREATED') };
    },
  };
}
