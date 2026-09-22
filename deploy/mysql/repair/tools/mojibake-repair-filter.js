// Filter + Chinese-mojibake (CP1252 double-encoding) repair for mysqldump-style SQL
const fs=require('fs'),readline=require('readline');
const opt={only:null,exclude:null,drop:false,src:'',out:''};
for(const a of process.argv.slice(2)){
  if(a.startsWith('--only='))opt.only=a.slice(7).split(',').filter(Boolean);
  else if(a.startsWith('--exclude='))opt.exclude=a.slice(10).split(',').filter(Boolean);
  else if(a==='--drop')opt.drop=true;
  else if(!opt.src)opt.src=a; else if(!opt.out)opt.out=a;
}
const SP={0x20AC:0x80,0x201A:0x82,0x0192:0x83,0x201E:0x84,0x2026:0x85,0x2020:0x86,0x2021:0x87,0x02C6:0x88,0x2030:0x89,0x0160:0x8A,0x2039:0x8B,0x0152:0x8C,0x017D:0x8E,0x2018:0x91,0x2019:0x92,0x201C:0x93,0x201D:0x94,0x2022:0x95,0x2013:0x96,0x2014:0x97,0x02DC:0x98,0x2122:0x99,0x0161:0x9A,0x203A:0x9B,0x0153:0x9C,0x017E:0x9E,0x0178:0x9F};
function b8(cp){ if(cp>=0xA0&&cp<=0xFF)return cp; if(cp>=0x80&&cp<=0x9F)return cp; const v=SP[cp]; return v===undefined?-1:v; }
const DEC=new TextDecoder('utf-8',{fatal:true});
let fixedLines=0;
function repairLine(line){
  let out='',i=0;const n=line.length;let any=false;
  while(i<n){
    const cp0=line.codePointAt(i);
    if(cp0>0xFFFF||b8(cp0)<0){out+=line[i];i++;continue;}
    let j=i,bytes=[],hi=false;
    while(j<n){const cp=line.codePointAt(j); if(cp>0xFFFF)break; const bb=b8(cp); if(bb<0)break; if(bb>=0x80)hi=true; bytes.push(bb); j++;}
    const run=line.slice(i,j);
    if(bytes.length>=2&&hi){ try{ out+=DEC.decode(Buffer.from(bytes)); any=true; }catch(e){ out+=run; } }
    else out+=run;
    i=j;
  }
  if(any)fixedLines++;
  return out;
}
const COMPAT=/[\u2E80-\u2FDF\uF900-\uFAFF]/g;
function fixCompat(s){ return COMPAT.test(s)?s.replace(COMPAT,ch=>ch.normalize('NFKC')):s; }
function clean(line){ return fixCompat(repairLine(line)); }
function kept(t){ if(!t)return true; if(opt.only&&!opt.only.some(p=>t.startsWith(p)))return false; if(opt.exclude&&opt.exclude.some(p=>t.startsWith(p)))return false; return true; }
const TBL=[/^\/\*!\d+\s+ALTER TABLE `([^`]+)`/,/^ALTER TABLE `([^`]+)`/,/^LOCK TABLES `([^`]+)`/,/^DROP TABLE IF EXISTS `([^`]+)`/,/^CREATE TABLE `([^`]+)`/,/^INSERT INTO `([^`]+)`/];
const CMT=/^-- (?:Table structure|Dumping data) for table `([^`]+)`/;
const SQLSTART=/^(INSERT\b|CREATE\b|DROP\b|ALTER\b|LOCK\b|UNLOCK\b|REPLACE\b|DELETE\b|UPDATE\b|SET\b|USE\b|DELIMITER\b|\/\*|--|\(|\)|;)/i;
(async()=>{
  const rl=readline.createInterface({input:fs.createReadStream(opt.src,{encoding:'utf8'}),crlfDelay:Infinity});
  const ws=fs.createWriteStream(opt.out,{encoding:'utf8'});
  const w=(s)=>ws.write(s+'\n');
  let seenTable=false, inStmt=false, stmtLines=[], stmtKeep=false;
  const saved=new Set(), dropped=new Set();
  let skippedJunk=0, skippedRestore=0;
  w('SET NAMES utf8mb4;');
  w('SET FOREIGN_KEY_CHECKS=0;');
  for await(const raw of rl){
    const line=raw.replace(/\r$/,'');
    if(inStmt){
      stmtLines.push(line);
      if(/;\s*$/.test(line)){
        if(stmtKeep)for(const l of stmtLines)w(clean(l));
        inStmt=false; stmtLines=[]; stmtKeep=false;
      }
      continue;
    }
    const tr=line.trim();
    if(tr===''||tr.startsWith('--')){
      const m=tr.match(CMT);
      if(m){ seenTable=true; if(kept(m[1]))w(clean(line)); }
      else if(!seenTable)w(clean(line));
      continue;
    }
    if(/^CREATE DATABASE\b/i.test(tr)||/^USE\s/i.test(tr))continue;
    if(!SQLSTART.test(tr)){ if(!seenTable){w(clean('-- '+line)); } else skippedJunk++; continue; }
    let t=null;
    for(const re of TBL){ const m=tr.match(re); if(m){ t=m[1]; break; } }
    if(t!==null)seenTable=true;
    inStmt=true; stmtLines=[line]; stmtKeep=kept(t);
    if(stmtKeep){
      const savers=tr.match(/@OLD_([A-Za-z0-9_]+)\s*=/g);
      if(savers&&/=\s*@@/.test(tr)){ for(const s of savers)saved.add(s.replace(/\s*=$/,'')); }
      else if(tr.includes('@OLD_')){
        const refs=(tr.match(/@OLD_[A-Za-z0-9_]+/g)||[]);
        if(refs.length&&!refs.some(r=>saved.has(r))){ stmtKeep=false; skippedRestore++; }
      }
      if(stmtKeep&&opt.drop&&/^CREATE TABLE /.test(tr)&&!dropped.has(t)){ w('DROP TABLE IF EXISTS `'+t+'`;'); dropped.add(t); }
    }
    if(/;\s*$/.test(line)){
      if(stmtKeep)w(clean(line));
      inStmt=false; stmtLines=[]; stmtKeep=false;
    }
  }
  w('SET FOREIGN_KEY_CHECKS=1;');
  ws.end();
  ws.on('finish',()=>console.log('OK '+opt.out.split(/[\\/]/).pop()+'  repairedLines='+fixedLines+'  skippedJunk='+skippedJunk+'  skippedRestore='+skippedRestore));
})();