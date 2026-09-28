// 通用「CP1252 双重编码」乱码修复：按行处理整份文件，不做任何过滤。
const fs=require('fs');
const src=process.argv[2], out=process.argv[3];
const SP={0x20AC:0x80,0x201A:0x82,0x0192:0x83,0x201E:0x84,0x2026:0x85,0x2020:0x86,0x2021:0x87,0x02C6:0x88,0x2030:0x89,0x0160:0x8A,0x2039:0x8B,0x0152:0x8C,0x017D:0x8E,0x2018:0x91,0x2019:0x92,0x201C:0x93,0x201D:0x94,0x2022:0x95,0x2013:0x96,0x2014:0x97,0x02DC:0x98,0x2122:0x99,0x0161:0x9A,0x203A:0x9B,0x0153:0x9C,0x017E:0x9E,0x0178:0x9F};
function b8(cp){ if(cp>=0xA0&&cp<=0xFF)return cp; if(cp>=0x80&&cp<=0x9F)return cp; const v=SP[cp]; return v===undefined?-1:v; }
const DEC=new TextDecoder('utf-8',{fatal:true});
let fixed=0;
function repairLine(line){
  let out='',i=0;const n=line.length;let any=false;
  while(i<n){
    const cp0=line.codePointAt(i);
    if(cp0>0xFFFF||b8(cp0)<0){out+=line[i];i++;continue;}
    let j=i,bytes=[],hi=false;
    while(j<n){const cp=line.codePointAt(j); if(cp>0xFFFF)break; const bb=b8(cp); if(bb<0)break; if(bb>=0x80)hi=true; bytes.push(bb); j++;}
    const run=line.slice(i,j);
    if(bytes.length>=2&&hi){ try{ out+=DEC.decode(Buffer.from(bytes)); any=true; }catch(e){ out+=run; } } else out+=run;
    i=j;
  }
  if(any)fixed++;
  return out;
}
const COMPAT=/[\u2E80-\u2FDF\uF900-\uFAFF]/g;
function fixCompat(s){ return COMPAT.test(s)?s.replace(COMPAT,ch=>ch.normalize('NFKC')):s; }
const text=fs.readFileSync(src,'utf8');
const outText=text.split('\n').map(l=>fixCompat(repairLine(l))).join('\n');
fs.writeFileSync(out,outText,'utf8');
console.log('repaired lines: '+fixed+' -> '+out);