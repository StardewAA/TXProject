// 批量把「防腐保温六标段」目录下的文件登记为 TENDER 材料并触发入库索引。
// 用 Node 原生 fetch + FormData + Blob，正确处理中文文件名（避免 shell 传参乱码）。
import fs from 'node:fs';
import path from 'node:path';

const BASE = 'http://localhost:48080';
const DIR = 'E:/防腐保温六标段';
const TENANT = '1';
const SECTION = '六标段';

async function main() {
  const login = await fetch(`${BASE}/admin-api/system/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'tenant-id': TENANT },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  }).then((r) => r.json());
  const token = login?.data?.accessToken;
  if (!token) {
    console.error('登录失败:', JSON.stringify(login));
    process.exit(1);
  }
  console.log('登录成功, token len =', token.length);

  const names = fs.readdirSync(DIR).filter((f) => {
    const full = path.join(DIR, f);
    return fs.statSync(full).isFile() && f.toLowerCase().endsWith('.pdf');
  });

  const results = [];
  for (const name of names) {
    const full = path.join(DIR, name);
    const content = fs.readFileSync(full);
    const t0 = Date.now();

    // 1. 上传
    const form = new FormData();
    form.append('file', new Blob([content]), name);
    form.append('directory', 'bidding/bid-agent');
    const up = await fetch(`${BASE}/admin-api/bidding/file/upload`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'tenant-id': TENANT },
      body: form,
    }).then((r) => r.json());
    const fileId = up?.data?.fileId;
    const url = up?.data?.url;
    if (!fileId) {
      console.log(`✗ 上传失败 ${name}: code=${up?.code} msg=${up?.msg}`);
      results.push({ name, ok: false, reason: 'upload' });
      continue;
    }

    // 2. 登记材料（register 内同步入库索引）
    const reg = await fetch(`${BASE}/admin-api/bidding/bid-agent/material/save`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        'tenant-id': TENANT,
      },
      body: JSON.stringify({
        docType: 'TENDER',
        name,
        bidSectionCode: SECTION,
        fileId,
        fileUrl: url,
        fileName: name,
        fileSize: content.length,
      }),
    }).then((r) => r.json());

    const ms = Date.now() - t0;
    if (reg?.code === 0) {
      console.log(`✓ ${name}  materialId=${reg.data}  耗时 ${(ms / 1000).toFixed(1)}s`);
      results.push({ name, ok: true, materialId: reg.data });
    } else {
      console.log(`✗ 登记失败 ${name}: code=${reg?.code} msg=${reg?.msg}`);
      results.push({ name, ok: false, reason: 'register', msg: reg?.msg });
    }
  }

  const ok = results.filter((r) => r.ok);
  console.log(`\n完成：${ok.length}/${results.length} 成功`);
  for (const r of results.filter((x) => !x.ok)) {
    console.log('  失败项:', r.name, r.reason, r.msg ?? '');
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
