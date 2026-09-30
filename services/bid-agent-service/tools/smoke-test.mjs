/**
 * 端到端冒烟测试：拉起 Java 契约 Mock + LLM Mock + bid-agent-service，
 * 依次跑通 parse / outline / draft 三类任务，自动应答 HIL 闸门。
 *
 * 用法：node tools/smoke-test.mjs
 */
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const nodeBin = process.execPath;
const tscBin = path.join(root, 'node_modules', 'typescript', 'bin', 'tsc');

const AGENT_PORT = 8199;
const JAVA_PORT = 48081;
const LLM_PORT = 8100;
const AGENT_BASE = `http://127.0.0.1:${AGENT_PORT}`;

const children = [];

function start(name, args, env = {}) {
  const child = spawn(nodeBin, args, {
    cwd: root,
    env: { ...process.env, ...env },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  child.stdout.on('data', (data) => {
    const text = String(data).trim();
    if (text) console.log(`  [${name}] ${text.split(/\r?\n/).join(`\n  [${name}] `)}`);
  });
  child.stderr.on('data', (data) => console.error(`  [${name}:err] ${String(data).trim()}`));
  children.push(child);
  return child;
}

async function waitForHealth(url, label, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(2000) });
      if (res.ok) return true;
    } catch {
      // 继续等待
    }
    await new Promise((resolve) => setTimeout(resolve, 300));
  }
  throw new Error(`${label} 启动超时：${url}`);
}

async function api(pathname, init = {}) {
  const res = await fetch(`${AGENT_BASE}${pathname}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${pathname} -> ${res.status}: ${text}`);
  return text ? JSON.parse(text) : {};
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function createTask(type, payload, projectId) {
  const created = await api('/agent/tasks', {
    method: 'POST',
    body: JSON.stringify({ tenantId: 'tenant-demo', projectId, taskType: type, payload }),
    headers: { 'Idempotency-Key': `smoke-${type}-${Date.now()}` },
  });
  return created.taskId;
}

async function waitTask(taskId, timeoutMs = 180_000) {
  const deadline = Date.now() + timeoutMs;
  let answered = 0;
  while (Date.now() < deadline) {
    const view = await api(`/agent/tasks/${taskId}`);
    if (view.status === 'awaiting_human') {
      for (const question of view.pendingQuestions) {
        await api(`/agent/tasks/${taskId}/confirm`, {
          method: 'POST',
          body: JSON.stringify({ answers: [{ questionId: question.questionId, value: '确认' }] }),
        });
        answered += 1;
      }
      await sleep(120);
      continue;
    }
    if (['completed', 'failed', 'interrupted', 'rolled_back'].includes(view.status)) {
      if (view.status === 'rolled_back') {
        await sleep(200);
        continue;
      }
      return { ...view, answeredQuestions: answered };
    }
    await sleep(200);
  }
  throw new Error(`任务 ${taskId} 超时未完成`);
}

async function runTask(type, payload, projectId) {
  const taskId = await createTask(type, payload, projectId);
  return waitTask(taskId);
}

async function main() {
  if (!fs.existsSync(path.join(root, 'dist', 'index.js'))) {
    console.log('未检测到 dist，先执行编译…');
    await new Promise((resolve, reject) => {
      const build = spawn(nodeBin, [tscBin, '-p', path.join(root, 'tsconfig.json')], {
        cwd: root,
        stdio: 'inherit',
      });
      build.on('exit', (code) => (code === 0 ? resolve() : reject(new Error(`编译失败 code=${code}`))));
    });
  }

  const smokeWorkspace = path.join(root, 'workspace-smoke');
  const smokeSessions = path.join(root, 'sessions-smoke');
  // 云端 LLM 模式：设 SMOKE_CLOUD_LLM_URL 后，agent 生成走云端（mock-java 仍提供契约数据）；
  // 未设置则回退本地 mock-llm。LLM_API_KEY 由 agent 读取 .env，故这里不注入。
  const cloudLlmUrl = process.env.SMOKE_CLOUD_LLM_URL ?? '';
  const cloudLlmModel = process.env.SMOKE_CLOUD_LLM_MODEL ?? 'qwen-plus';
  console.log('启动依赖服务…');
  start('mock-java', [path.join(here, 'mock-java-api.mjs')], { MOCK_JAVA_PORT: String(JAVA_PORT) });
  start('mock-llm', [path.join(here, 'mock-llm.mjs')], { MOCK_LLM_PORT: String(LLM_PORT) });
  start('agent', [path.join(root, 'dist', 'index.js')], {
    AGENT_HOST: '127.0.0.1',
    AGENT_PORT: String(AGENT_PORT),
    LOG_LEVEL: 'info',
    JAVA_BASE_URL: `http://127.0.0.1:${JAVA_PORT}`,
    JAVA_SHADOW_STATUS_PATH: '/admin-api/bid/agent/task-status',
    LLM_BASE_URL: cloudLlmUrl || `http://127.0.0.1:${LLM_PORT}/v1`,
    LLM_MODEL: cloudLlmUrl ? cloudLlmModel : 'mock-model',
    LLM_TIMEOUT_MS: cloudLlmUrl ? '180000' : '30000',
    RETRIEVE_TOPK: '5',
    WORKSPACE_DIR: smokeWorkspace,
    SESSIONS_DIR: smokeSessions,
  });

  await waitForHealth(`${AGENT_BASE}/healthz`, 'agent');
  console.log('agent 健康检查通过\n');

  const projectId = 'PRJ-SMOKE-001';
  const results = [];
  for (const type of ['parse', 'outline', 'draft']) {
    const view = await runTask(type, {}, projectId);
    results.push({
      type,
      status: view.status,
      phase: view.phase,
      progress: view.progress,
      hilAnswers: view.answeredQuestions,
      error: view.error?.message ?? '',
      summary: String(view.result?.summary ?? '').slice(0, 90),
    });
    console.log(
      `✓ ${type.padEnd(8)} status=${view.status} phase=${view.phase} HIL=${view.answeredQuestions} ${String(
        view.result?.summary ?? '',
      ).slice(0, 80)}`,
    );
    if (view.status !== 'completed') {
      console.log(`  错误详情：${JSON.stringify(view.error)}`);
    }
  }

  // 回退重跑：draft 任务回滚到 WRITE 阶段后再跑一遍
  const rollbackTaskId = await createTask('draft', {}, projectId);
  await waitTask(rollbackTaskId);
  const rollback = await api(`/agent/tasks/${rollbackTaskId}/rollback`, {
    method: 'POST',
    body: JSON.stringify({ toPhase: 'WRITE', keepSession: true }),
  });
  const rerun = await waitTask(rollbackTaskId);
  console.log(`✓ rollback  status=${rerun.status} phase=${rerun.phase}（回滚到 ${rollback.phase} 后重跑）`);

  // 中断：启动一个任务后立即中断
  const toInterrupt = await api('/agent/tasks', {
    method: 'POST',
    body: JSON.stringify({ tenantId: 'tenant-demo', projectId, taskType: 'parse' }),
  });
  await api(`/agent/tasks/${toInterrupt.taskId}/interrupt`, { method: 'POST' });
  const interrupted = await api(`/agent/tasks/${toInterrupt.taskId}`);
  console.log(`✓ interrupt status=${interrupted.status}`);

  console.log('\n任务结果汇总：');
  console.table(results);
  console.log(`工作区：${smokeWorkspace}`);
  console.log(`会话/检查点：${smokeSessions}`);

  const failed = results.filter((item) => item.status !== 'completed');
  console.log(failed.length === 0 ? '\n冒烟测试全部通过 ✅' : `\n存在失败任务：${failed.map((item) => item.type).join(', ')}`);
}

function cleanup() {
  for (const child of children) {
    if (!child.killed) child.kill();
  }
}

main()
  .catch((error) => {
    console.error(`冒烟测试失败：${error.stack ?? error.message}`);
    process.exitCode = 1;
  })
  .finally(() => {
    cleanup();
    setTimeout(() => process.exit(process.exitCode ?? 0), 300).unref();
  });
