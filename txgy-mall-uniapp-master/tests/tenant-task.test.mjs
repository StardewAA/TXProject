import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const source=readFileSync(new URL('../sheep/hooks/useTenantTask.js',import.meta.url),'utf8').replace(/^import[^\n]+\r?\n/gm,'').replace('export function','function');
function harness(){let epoch=1,dispose,expired=0;const listeners=new Set();const use=new Function('onScopeDispose','captureTenantOperation','isTenantOperationCurrent','uni',source+';return useTenantTask;')(
 fn=>dispose=fn,()=>epoch,s=>s===epoch,{$on:(_,fn)=>listeners.add(fn),$off:(_,fn)=>listeners.delete(fn)});
 return{task:use(()=>expired++),change(){epoch++;listeners.forEach(fn=>fn());},dispose:()=>dispose(),listeners,expired:()=>expired};}
test('page hide invalidates old callbacks, new operation can continue same account',()=>{const h=harness(),old=h.task.capture();h.task.invalidate();assert.equal(old(),false);assert.equal(h.task.capture()(),true);});
test('account change permanently expires page ownership',()=>{const h=harness(),old=h.task.capture();h.change();assert.equal(old(),false);assert.equal(h.task.current(),false);assert.equal(h.expired(),1);});
test('unmount unregisters session listener and stops deferred work',()=>{const h=harness(),old=h.task.capture();h.dispose();assert.equal(old(),false);assert.equal(h.listeners.size,0);});
