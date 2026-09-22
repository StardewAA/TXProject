import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const source=readFileSync(new URL('../sheep/components/s-uploader/choose-and-upload-file.js',import.meta.url),'utf8')
 .replace(/^import[^\n]+\r?\n/gm,'').replace(/export \{[^}]*\};?/g,'').replace('import.meta.env.SHOPRO_UPLOAD_TYPE','mode');
const flush=async()=>{for(let i=0;i<12;i++)await Promise.resolve();};
function harness(mode='server'){
 let epoch=1,current=true;const uploads=[],puts=[],created=[],clouds=[];
 const deps={mode,uni:{request:o=>puts.push(o)},uniCloud:{uploadFile:options=>new Promise(resolve=>clouds.push({options,resolve}))},captureTenantOperation:()=>epoch,isTenantOperationCurrent:s=>s===epoch,
  prepareTenantUpload:async()=>({snapshot:epoch}),isTenantRequestCurrent:s=>s===epoch,
  FileApi:{uploadFile:async(...args)=>{uploads.push(args);return{code:0,data:'https://files.test/a'};},
   getFilePresignedUrl:async()=>({code:0,data:{url:'https://files.test/a',uploadUrl:'https://files.test/signed'}}),
   createFile:async data=>{created.push(data);return{code:0};}}};
 const api=new Function(...Object.keys(deps),source+';return {uploadFiles,uploadCloudFiles};')(...Object.values(deps));
 return{...api,uploads,puts,created,clouds,change:()=>epoch++,leave:()=>current=false,current:()=>current};
}
const file=()=>({name:'a.png',path:'local',type:'image/png',arrayBuffer:async()=>new ArrayBuffer(1)});
test('switching while choosing files does not upload as replacement account',async()=>{
 const h=harness();let release;const p=h.uploadFiles(new Promise(r=>release=r),{});h.change();release({tempFiles:[file()]});await assert.rejects(p);assert.equal(h.uploads.length,0);
});
test('switching during asynchronous onChooseFile prevents server upload',async()=>{
 const h=harness();let release;const p=h.uploadFiles(Promise.resolve({tempFiles:[file()]}),{onChooseFile:()=>new Promise(r=>release=r)});await flush();h.change();release();await assert.rejects(p);assert.equal(h.uploads.length,0);
});
test('leaving during asynchronous onChooseFile prevents server upload',async()=>{
 const h=harness();let release;const p=h.uploadFiles(Promise.resolve({tempFiles:[file()]}),{isCurrent:h.current,onChooseFile:()=>new Promise(r=>release=r)});await flush();h.leave();release();await assert.rejects(p);assert.equal(h.uploads.length,0);
});
test('direct upload cannot start PUT after identity changes while reading bytes',async()=>{
 const h=harness('client');let release;const f=file();f.arrayBuffer=()=>new Promise(r=>release=r);
 const p=h.uploadFiles(Promise.resolve({tempFiles:[f]}),{});await flush();h.change();release(new ArrayBuffer(1));await assert.rejects(p);assert.equal(h.puts.length,0);assert.equal(h.created.length,0);
});
test('direct upload returning after page leave cannot register file',async()=>{
 const h=harness('client');const p=h.uploadFiles(Promise.resolve({tempFiles:[file()]}),{isCurrent:h.current});await flush();h.leave();h.puts[0].success({statusCode:200});await assert.rejects(p);assert.equal(h.created.length,0);
});
test('server upload success returns URL and forwards operation predicate',async()=>{
 const h=harness();const rows=await h.uploadFiles(Promise.resolve({tempFiles:[file()]}),{isCurrent:h.current});assert.equal(rows[0].url,'https://files.test/a');assert.equal(h.uploads[0][3],h.current);
});
test('direct upload success registers then returns URL',async()=>{
 const h=harness('client');const p=h.uploadFiles(Promise.resolve({tempFiles:[file()]}),{});await flush();h.puts[0].success({statusCode:200});assert.equal((await p)[0].url,'https://files.test/a');assert.equal(h.created.length,1);
});
test('cloud retry on a departed page does not start native requests',async()=>{
 const h=harness();h.leave();const files=[{uuid:1,path:'a'}];
 assert.deepEqual(await h.uploadCloudFiles.call({files},files,1,undefined,h.current),[]);assert.equal(h.clouds.length,0);
});
test('cloud retry stops queued files and discards late results after switching',async()=>{
 const h=harness();const files=[{uuid:1,path:'a'},{uuid:2,path:'b'}];
 const p=h.uploadCloudFiles.call({files},files,1,undefined,h.current);h.change();h.clouds[0].resolve({fileID:'cloud:old'});
 assert.deepEqual(await p,[]);assert.equal(h.clouds.length,1);
});
