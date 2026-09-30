#!/usr/bin/env node
/**
 * 标书生成 E2E 脚本（UTF-8 安全版）。
 *
 * 背景（2026-09-21）：此前用 PowerShell/curl 手写请求体跑 E2E 时，中文 remark/文件名
 * 按 GBK 编码发出，Java 侧按 UTF-8 解码后写入数据库的是不可逆的乱码（U+FFFD），污染了
 * 「我的标书」列表。本脚本用 Node 原生 fetch + JSON.stringify，请求体恒为 UTF-8，
 * 从根上避免该问题。
 *
 * 如果坚持用 PowerShell 调同一批接口，请：
 *   $json = $obj | ConvertTo-Json -Depth 6
 *   $bytes = [Text.Encoding]::UTF8.GetBytes($json)          # 显式 UTF-8 字节
 *   Invoke-RestMethod -Uri $uri -Headers @{ 'Content-Type' = 'application/json; charset=utf-8'; 'tenant-id' = '1' } -Body $bytes
 * 并注意 .ps1 文件本身要存成 UTF-8 with BOM（无 BOM 时 PS 5.1 按 ANSI 读源码，中文常量先乱一步）。
 *
 * 用法：
 *   node e2e-generate.mjs                              # 用现有最新 TENDER 物料发起生成
 *   node e2e-generate.mjs --tender ./招标文件.pdf      # 先上传+登记物料，再发起生成
 *   node e2e-generate.mjs --remark 'E2E-标书时间验证'  # 自定义备注（默认含中文，验证编码）
 *
 * 环境变量（均有默认值，指向本机网关）：
 *   BASE=http://127.0.0.1:48080  TENANT_ID=1  USER=admin  PASS=admin123
 *   POLL_SECONDS=5  TIMEOUT_MIN=30
 */

const BASE = process.env.BASE ?? 'http://127.0.0.1:48080';
const TENANT_ID = process.env.TENANT_ID ?? '1';
const USER = process.env.USER ?? 'admin';
const PASS = process.env.PASS ?? 'admin123';
const POLL_SECONDS = Number(process.env.POLL_SECONDS ?? 5);
const TIMEOUT_MIN = Number(process.env.TIMEOUT_MIN ?? 30);

const args = process.argv.slice(2);
const opt = (name) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? args[i + 1] : undefined;
};
const tenderFile = opt('tender');
const remark = opt('remark') ?? 'E2E-标书生成验证';
const docCategory = opt('category') ?? 'technical';

function fail(msg) {
  console.error(`✗ ${msg}`);
  process.exit(1);
}

async function call(method, path, { body, form } = {}) {
  const headers = { 'tenant-id': TENANT_ID, Authorization: `Bearer ${accessToken}` };
  let payload;
  if (form) {
    payload = form; // FormData：fetch 自动带 boundary 头
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json; charset=utf-8';
    payload = JSON.stringify(body); // UTF-8 编码由 Node/fetch 保证
  }
  const res = await fetch(`${BASE}/admin-api${path}`, { method, headers, body: payload });
  const json = await res.json().catch(() => ({}));
  if (res.status !== 200 || json.code !== 0) {
    throw new Error(`${method} ${path} → HTTP ${res.status}, code=${json.code}, msg=${json.msg ?? json.message ?? ''}`);
  }
  return json.data;
}

let accessToken = '';
async function login() {
  const res = await fetch(`${BASE}/admin-api/system/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8', 'tenant-id': TENANT_ID },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  const json = await res.json();
  if (json.code !== 0) fail(`登录失败：${json.msg}`);
  accessToken = json.data.accessToken;
  console.log(`✓ 已登录（tenant=${TENANT_ID}, user=${USER}）`);
}

async function uploadAndRegister(filePath) {
  const fs = await import('node:fs');
  if (!fs.existsSync(filePath)) fail(`招标文件不存在：${filePath}`);
  const name = filePath.split(/[\\/]/).pop();
  const form = new FormData();
  form.append('file', new Blob([fs.readFileSync(filePath)]), name); // 文件名走 multipart filename*（UTF-8 安全）
  form.append('directory', 'bidding/bid-agent');
  const uploaded = await call('POST', '/bidding/file/upload', { form });
  const materialId = await call('POST', '/bidding/bid-agent/material/save', {
    body: {
      docType: 'TENDER',
      name, // 中文文件名原样登记（UTF-8 JSON）
      fileId: uploaded.fileId,
      fileUrl: uploaded.url,
      fileName: uploaded.name ?? name,
      fileSize: uploaded.size,
    },
  });
  console.log(`✓ 物料已登记：${name}（materialId=${materialId}）`);
  return materialId;
}

async function pickExistingTender() {
  const page = await call('GET', '/bidding/bid-agent/material/page?pageNo=1&pageSize=50');
  const tender = (page?.list ?? []).find((m) => m.docType === 'TENDER');
  if (!tender) fail('没有可用的 TENDER 物料，请先用 --tender <文件> 上传一份');
  console.log(`✓ 复用现有招标物料：${tender.fileName ?? tender.name}（materialId=${tender.id}）`);
  return tender.id;
}

async function main() {
  await login();
  const materialId = tenderFile ? await uploadAndRegister(tenderFile) : await pickExistingTender();

  const taskId = await call('POST', '/bidding/bid-agent/task/generate', {
    body: { tenderMaterialId: materialId, docCategory, remark },
  });
  console.log(`✓ 生成任务已创建：taskId=${taskId}，remark=${remark}（docCategory=${docCategory}）`);

  const deadline = Date.now() + TIMEOUT_MIN * 60_000;
  for (;;) {
    await new Promise((r) => setTimeout(r, POLL_SECONDS * 1000));
    const t = await call('GET', `/bidding/bid-agent/task/get?id=${taskId}`);
    console.log(`  [${t.status}] ${t.currentStep ?? ''} ${t.progress ?? 0}%`);
    if (['SUCCEEDED', 'FAILED', 'CANCELED'].includes(t.status)) {
      if (t.status === 'SUCCEEDED') {
        console.log(`✓ 生成成功，产物：${t.artifactUrl ?? '(未生成)'}`);
      } else {
        fail(`任务 ${t.status}：${t.errorMessage ?? ''}`);
      }
      return;
    }
    if (Date.now() > deadline) fail(`轮询超时（${TIMEOUT_MIN} 分钟）`);
  }
}

main().catch((err) => fail(err.message));
