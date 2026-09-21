import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const source=readFileSync(new URL('../pages/chat/util/useWebSocket.js',import.meta.url),'utf8').replace(/^import[^\n]+\r?\n/gm,'').replace('export function','function');
function harness(tenant='1'){
 let epoch=1,dispose,messages=0,closed=0;const sockets=[],listeners=new Set(),timers=new Set();
 const deps={onBeforeUnmount:fn=>dispose=fn,reactive:x=>x,ref:value=>({value}),baseUrl:'http://local',websocketPath:'/ws',tenantId:1,
  getControlledTenantId:()=>tenant,captureTenantOperation:()=>epoch,isTenantOperationCurrent:s=>s===epoch,
  getRefreshToken:()=> 'fake',copyValueToTarget:(target,value)=>Object.assign(target,value),
  setInterval:fn=>{timers.add(fn);return fn;},clearInterval:fn=>timers.delete(fn),setTimeout:fn=>{timers.add(fn);return fn;},clearTimeout:fn=>timers.delete(fn),
  uni:{$on:(_,fn)=>listeners.add(fn),$off:(_,fn)=>listeners.delete(fn),connectSocket(){const s={onOpen:fn=>s.open=fn,onMessage:fn=>s.message=fn,onClose:fn=>s.closed=fn,send(){},close(){closed++;s.closed?.({});}};sockets.push(s);return s;}}};
 const use=new Function(...Object.keys(deps),source+';return useWebSocket;')(...Object.values(deps));
 use({onMessage:()=>messages++});
 return {sockets,listeners,timers,change(){epoch++;listeners.forEach(fn=>fn());},dispose:()=>dispose(),counts:()=>({messages,closed})};
}
test('legacy socket forwards messages only in original session',()=>{const h=harness();h.sockets[0].message({data:'{"type":"message"}'});assert.equal(h.counts().messages,1);h.dispose();});
test('account change closes connection, stops timers and discards old messages',()=>{const h=harness();h.sockets[0].open();h.change();h.sockets[0].message({data:'{"type":"message"}'});assert.equal(h.counts().messages,0);assert.equal(h.counts().closed,1);assert.equal(h.timers.size,0);h.dispose();});
test('unmount removes session listener and closes connection',()=>{const h=harness();h.dispose();assert.equal(h.listeners.size,0);assert.equal(h.counts().closed,1);});
test('foreign entry still cannot use legacy realtime transport',()=>{const h=harness('9001');assert.equal(h.sockets.length,0);h.dispose();});
