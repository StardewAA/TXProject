import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { tenantShareLink } from '../sheep/helper/tenant-navigation.mjs';
const source=readFileSync(new URL('../sheep/platform/share.js',import.meta.url),'utf8')
  .replace(/^import .*;\r?\n/gm,'').replace(/export default \{[\s\S]*?\};\s*$/,'');
function harness({id,tenant='1',address='',logged=true}={}) {
  const api=new Function('$store','$platform','$router','$url','BrokerageApi','SharePageEnum','isSimpleMarketingEnabled','getControlledTenantId','tenantTokenKey','tenantShareLink','window',source+'\nreturn {getShareInfo};')
    (name=>name==='user'?{isLogin:logged,userInfo:{id}}:{platform:{share:{methods:['forward','link'],linkAddress:address}}},
      {name:'H5'},{},{cdn:x=>x},{},{HOME:{value:'1'}},()=>true,()=>tenant,key=>key,tenantShareLink,{location:{origin:'https://mall'}});
  return api;
}
test('stored token without loaded member profile still renders anonymous share safely',()=>{
  const info=harness().getShareInfo();assert.equal(info.query,'spm=0.1.0.1.1&tenantId=1');
  assert.equal(info.path,'pages/index/index?'+info.query);assert.equal(info.forward.path,info.path);
});
test('actual share API keeps product id, correct copy-link channel and target tenant',()=>{
  const info=harness({id:9,tenant:'9001'}).getShareInfo({title:'材料',image:'/image.png',params:{page:'2',query:7,from:'link'}});
  assert.equal(info.query,'spm=9.2.7.1.3&tenantId=9001');
  assert.equal(info.link,'https://mall/pages/index/index?'+info.query);
});
test('configured hash share keeps a single route query, strips code and uses legacy scope explicitly',()=>{
  const info=harness({address:'https://mall/#/pages/index/index?code=PRIVATE'}).getShareInfo();
  assert.equal(info.link,'https://mall/#/pages/index/index?spm=0.1.0.1.1&tenantId=1');
  assert(!info.link.includes('PRIVATE'));
});
