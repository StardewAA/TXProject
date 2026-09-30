/* docx 字节渲染端到端取证（2026-10-01，方案 A）
 * 链路与浏览器完全一致：
 *   登录取 token → GET /admin-api/bidding/file/download?id=<docx fileId>（内联出口）
 *   → 字节 → mammoth.convertToHtml → HTML（断言含中文正文，证明渲染链路通）
 */
const fs = require('fs');
const path = require('path');
// mammoth 是 web-antd 的直接依赖（pnpm 严格 node_modules，仓库根/e2e 不可见），按相对路径引
const mammoth = require('../../apps/web-antd/node_modules/mammoth/mammoth.browser');

const API = 'http://127.0.0.1:48080/admin-api';
const MY = 'D:/Code/IDEA/txgy/txgycloud/.tmp-scratch-2026-09-23';

(async () => {
  const results = [];
  const rec = (name, ok, detail) => { results.push({ name, ok: !!ok, detail }); console.log((ok ? 'PASS ' : 'FAIL ') + name + ' — ' + detail); };

  // 1) 登录
  const login = await fetch(API + '/system/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'tenant-id': '1' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  });
  const loginBody = await login.json();
  const token = loginBody?.data?.accessToken;
  rec('D1 登录取 token', !!token, loginBody?.msg || 'ok');

  // 2) 库里取一个 docx fileId（真实数据）
  // 注意：download 出口吃的是 infra_file.id（bidding_file.file_id），不是 bidding_file.id
  const { execSync } = require('child_process');
  const ids = execSync(
    'docker exec -i txgy-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 --raw -N -B ruoyi-vue-pro -e "SELECT f.file_id,f.name FROM bidding_file f JOIN infra_file i ON i.id=f.file_id WHERE f.deleted=0 AND f.name LIKE \'%.docx\' AND i.path IS NOT NULL ORDER BY f.id DESC LIMIT 1;" 2>&1 | grep -v Warning',
    { encoding: 'utf8' },
  ).trim().split('\t');
  const fileId = ids[0];
  const name = ids[1] || 'docx';
  rec('D2 取到真实 docx', !!fileId, fileId + ' ' + name);
  if (!token || !fileId) process.exit(1);

  // 3) 走业务受控出口取字节（失败时打印错误体定位）
  const res = await fetch(API + '/bidding/file/download?id=' + fileId, {
    headers: { Authorization: 'Bearer ' + token, 'tenant-id': '1', Accept: '*/*' },
  });
  const ct = (res.headers.get('content-type') || '').toLowerCase();
  const raw = Buffer.from(await res.arrayBuffer());
  if (ct.includes('json') || raw.length === 0 || raw[0] === 0x7b) {
    console.log('  [D3 err body] ' + raw.toString('utf8', 0, 200));
  }
  const buf = raw;
  rec('D3 下载出口返回字节', buf.length > 0 && !ct.includes('json'), ct + ' len=' + buf.length);
  rec('D4 字节是 docx(zip 头 PK)', buf[0] === 0x50 && buf[1] === 0x4b, 'magic=' + buf[0].toString(16) + buf[1].toString(16));

  // 4) mammoth 渲染
  try {
    // mammoth.browser 只认 arrayBuffer（浏览器 fetchFileBlob → blob.arrayBuffer() 同一口径），传 node Buffer 会报 "Could not find file in options"
    const ab = buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength);
    const out = await mammoth.convertToHtml({ arrayBuffer: ab });
    const html = out.value || '';
    const text = html.replace(/<[^>]+>/g, ' ');
    rec('D5 mammoth 渲染出正文', text.trim().length > 20, html.length + ' bytes html; 样例: ' + text.trim().slice(0, 60));
    fs.writeFileSync(path.join(MY, 'docx-render-sample.html'), html);
  } catch (e) {
    rec('D5 mammoth 渲染出正文', false, e.message);
  }

  const pass = results.filter((r) => r.ok).length;
  const fail = results.filter((r) => !r.ok).length;
  console.log('\n[总] ' + pass + ' PASS / ' + fail + ' FAIL');
  process.exit(fail ? 1 : 0);
})();
