import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createTenantUploader } from '../sheep/helper/tenant-upload.mjs';
const flush = async () => { for(let i=0;i<12;i++)await Promise.resolve(); };
function harness(extra={}) {
  const calls=[],listeners=new Set(),timers=new Set();let epoch=1,shows=0,hides=0,aborts=0;
  const api=createTenantUploader({prepare:async()=>({snapshot:epoch,header:{Authorization:'A','tenant-id':'1'}}),isCurrent:s=>s===epoch,
    upload:options=>{calls.push(options);return{abort(){aborts++;options.fail();options.complete();}};},
    showLoading:()=>shows++,hideLoading:()=>hides++,subscribe:fn=>{listeners.add(fn);return()=>listeners.delete(fn);},
    schedule:fn=>{timers.add(fn);return fn;},cancel:fn=>timers.delete(fn),...extra});
  return {api,calls,listeners,timers,change(){epoch++;listeners.forEach(fn=>fn());},counts:()=>({shows,hides,aborts})};
}
const ok={statusCode:200,data:JSON.stringify({code:0,data:'https://files.test/a.png'})};
test('upload preserves verified headers and directory; completes once',async()=>{
 const h=harness(),p=h.api('file','mall');await flush();const c=h.calls[0];
 assert.equal(c.header['tenant-id'],'1');assert.equal(c.header.Authorization,'A');assert.equal(c.formData.directory,'mall');
 c.success(ok);c.fail();c.complete();assert.equal((await p).data,'https://files.test/a.png');
 assert.deepEqual(h.counts(),{shows:1,hides:1,aborts:0});assert.equal(h.listeners.size,0);assert.equal(h.timers.size,0);
});
for(const [name,response] of Object.entries({malformed:{statusCode:200,data:'<html>'},http:{statusCode:500,data:ok.data},business:{statusCode:200,data:'{"code":500,"msg":"失败"}'},legacyError:{statusCode:200,data:'{"error":1}'},empty:{statusCode:200,data:'{"code":0,"data":""}'},invalid:{statusCode:200,data:'null'}})){
 test(name+' response rejects and releases spinner',async()=>{const h=harness(),p=h.api('f');await flush();h.calls[0].success(response);await assert.rejects(p);assert.equal(h.counts().hides,1);assert.equal(h.listeners.size,0);});
}
for(const event of ['fail','complete'])test(event+' without success settles',async()=>{const h=harness(),p=h.api('f');await flush();h.calls[0][event]();await assert.rejects(p);assert.equal(h.counts().hides,1);});
test('synchronous native exception releases spinner',async()=>{const h=harness({upload(){throw Error('native');}});await assert.rejects(h.api('f'));assert.equal(h.counts().hides,1);assert.equal(h.listeners.size,0);});
test('timeout aborts task, rejects and unregisters listener',async()=>{const h=harness(),p=h.api('f');await flush();[...h.timers][0]();await assert.rejects(p,/超时/);assert.equal(h.counts().aborts,1);assert.equal(h.timers.size,0);});
test('session change aborts old upload and ignores late success',async()=>{const h=harness(),p=h.api('f');await flush();h.change();h.calls[0].success(ok);await assert.rejects(p,/变化/);assert.equal(h.counts().aborts,1);assert.equal(h.listeners.size,0);});
test('expired expected snapshot blocks before preparing another account',async()=>{let prepared=0;const h=harness({prepare:()=>{prepared++;}});await assert.rejects(h.api('f','',9));assert.equal(prepared,0);});
test('account change during preparation prevents native upload',async()=>{let release;const h=harness({prepare:()=>new Promise(r=>release=r)});const p=h.api('f');h.change();release({snapshot:1,header:{}});await assert.rejects(p);assert.equal(h.calls.length,0);});
test('page leave suppresses late result',async()=>{let current=true;const h=harness(),p=h.api('f','',undefined,()=>current);await flush();current=false;h.calls[0].success(ok);await assert.rejects(p);});
test('concurrent uploads keep loading until both settle',async()=>{const h=harness(),a=h.api('a'),b=h.api('b');await flush();h.calls[0].success(ok);await a;assert.equal(h.counts().hides,0);h.calls[1].success(ok);await b;assert.equal(h.counts().shows,1);assert.equal(h.counts().hides,1);});
