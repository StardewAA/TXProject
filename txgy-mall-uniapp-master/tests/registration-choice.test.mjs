import {test} from 'node:test';
import {parseRegistrationOptions,resolveRegistrationChoice,assertRegistrationContext,assertExplicitRegistrationTarget,registrationChoiceNotice} from '../sheep/helper/registration-choice.mjs';
import {createChallengeState} from '../sheep/helper/auth-challenge.mjs';
import assert from 'node:assert/strict';
const data=()=>({ enabled:true,publicTenantId:null,tenants:[{tenantId:1,name:'当前企业商城'},{tenantId:9002,name:'其他商城'}],crossTenantReady:false,selectionRequired:true });
test('disabled configuration exposes no retained directory',()=>{
 assert.deepEqual(parseRegistrationOptions({...data(),enabled:false}),{enabled:false,publicTenantId:null,tenants:[],crossTenantReady:false,selectionRequired:false});
});
test('only minimal normalized public fields are retained',()=>{
 const value=parseRegistrationOptions({...data(),secret:'private'});
 assert.equal(value.tenants[0].tenantId,'1');assert.equal(value.secret,undefined);
});
test('default, retired tenant and malformed directory fail closed',()=>{
 for(const value of [null,{}, {...data(),publicTenantId:1},{...data(),selectionRequired:false},{...data(),selectionRequired:undefined},{...data(),tenants:[]},
  {...data(),tenants:[{tenantId:3000,name:'已隔离公共租户'}]},{...data(),tenants:[{tenantId:9001,name:''}]},{...data(),tenants:[{tenantId:9001,name:'A'},{tenantId:9001,name:'B'}]},
  {...data(),enabled:'true'},{...data(),crossTenantReady:undefined}]) assert.throws(()=>parseRegistrationOptions(value));
});
test('empty selection cannot default to any tenant',()=>{
 const options=parseRegistrationOptions(data());
 for(const value of ['',null,undefined]) assert.throws(()=>resolveRegistrationChoice(options,value,'1'),/请选择/);
});
test('explicit selection must remain in current registration directory',()=>{
 const options=parseRegistrationOptions(data());assert.equal(resolveRegistrationChoice(options,'1','1').tenantId,'1');
 for(const value of ['3000','0','1.0',' 1','1,9001']) assert.throws(()=>resolveRegistrationChoice(options,value,'1'));
});
test('removed option cannot silently change to public default',()=>{
 const options=parseRegistrationOptions(data());options.tenants=options.tenants.filter(t=>t.tenantId!=='1');
 assert.throws(()=>resolveRegistrationChoice(options,'1','1'));
});
test('legacy disabled choice preserves current entry',()=>{
 assert.equal(resolveRegistrationChoice(null,'9001','1').tenantId,'1');
});
test('cross tenant readiness claim cannot bypass fixed-entry business barrier',()=>{
 const options=parseRegistrationOptions({...data(),crossTenantReady:true});
 assert.throws(()=>assertRegistrationContext(resolveRegistrationChoice(options,'','1'),'1'));
 assert.doesNotThrow(()=>assertRegistrationContext(resolveRegistrationChoice(options,'1','1'),'1'));
});
test('confirmation describes the explicit tenant without enterprise permission',()=>{
 const notice=registrationChoiceNotice(resolveRegistrationChoice(parseRegistrationOptions(data()),'1','1'));
 assert.match(notice,/当前企业商城/);assert.doesNotMatch(notice,/公共商城/);assert.match(notice,/不代表取得企业采购或销售权限/);
});
test('PC-style target must be explicit, current and never tenant 3000',()=>{
 assert.equal(assertExplicitRegistrationTarget('1','1'),'1');
 for(const value of [undefined,'',3000,'3000','9002']) assert.throws(()=>assertExplicitRegistrationTarget(value,'1'));
});
test('changed tenant invalidates old verification challenge even after switching back',()=>{
 const state=createChallengeState();const a={tenantId:'1',mobile:'19900000000',purpose:'REGISTER',protocol:'challenge-v1'};
 const ticket=state.begin(a);
 state.change({...a,tenantId:'9001'});state.change(a);
 assert.equal(state.accept(ticket,{tenantId:1,challengeId:'a'.repeat(32),expiresIn:300,retryAfter:60,deliveryStatus:'SUBMITTED_UNCONFIRMED'}),false);
 assert.throws(()=>state.payload(a,'123456'));
});
test('directory refresh invalidates challenge even if selected tenant stays the same',()=>{
 const state=createChallengeState(),a={tenantId:'1',mobile:'19900000000',purpose:'REGISTER',protocol:'challenge-v1'};
 const ticket=state.begin(a);state.invalidate();
 assert.equal(state.current(ticket),false);assert.throws(()=>state.payload(a,'123456'));
});
