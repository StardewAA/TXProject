import fs from 'node:fs';
import { loadConfig } from './config.js';
import { AppError, normalizeError } from './errors.js';
import { JavaApiClient } from './client/javaApi.js';
import { createHttpServer } from './http/server.js';
import { Metrics } from './lib/metrics.js';
import { listBookmarkNames } from './lib/bookmarkFill.js';
import { loadBookmarkDict } from './lib/bookmarkFields.js';
import { Logger, parseLogLevel } from './logger.js';
import { HilService } from './orchestrator/hil.js';
import { TaskService } from './orchestrator/taskService.js';

/**
 * 启动一致性校验（字典版本管理）：比对「模板实际书签集合」与「字典声明书签集合」，
 * 不一致仅告警不阻断启动（回填仍可进行，但字典可能漏配新书签）。
 */
function checkTemplateDictConsistency(config: ReturnType<typeof loadConfig>, logger: Logger): void {
  if (!fs.existsSync(config.docx.templatePath)) {
    logger.warn('标书模板不存在，交付将回退内置预览排版', { template: config.docx.templatePath });
    return;
  }
  const dict = loadBookmarkDict();
  if (!dict) {
    logger.warn('未找到 bookmark-dict.json（书签字典），回填将使用内置硬编码映射兜底；可运行 tools/gen-bookmark-dict.mjs 生成');
    return;
  }
  let templateNames: string[];
  try {
    templateNames = listBookmarkNames(fs.readFileSync(config.docx.templatePath));
  } catch (error) {
    logger.warn('模板书签解析失败，跳过字典一致性校验', { error: error instanceof Error ? error.message : String(error) });
    return;
  }
  if (templateNames.length === 0) {
    logger.warn('模板不含书签（非书签占位模板），字典一致性校验不适用', { template: config.docx.templatePath });
    return;
  }
  const dictNames = new Set(dict.bookmarks.map((entry) => entry.name));
  const templateOnly = templateNames.filter((name) => !dictNames.has(name));
  const dictOnly = dict.bookmarks.map((entry) => entry.name).filter((name) => !templateNames.includes(name));
  if (templateOnly.length > 0 || dictOnly.length > 0) {
    logger.warn('模板书签与字典不一致：请重新运行 tools/gen-bookmark-dict.mjs 生成字典', {
      template: config.docx.templatePath,
      dictVersion: dict.version,
      templateOnlyCount: templateOnly.length,
      dictOnlyCount: dictOnly.length,
      templateOnly: templateOnly.slice(0, 20),
      dictOnly: dictOnly.slice(0, 20),
    });
    return;
  }
  logger.info('模板书签与字典一致性校验通过', {
    template: config.docx.templatePath,
    dictVersion: dict.version,
    bookmarks: templateNames.length,
  });
}

async function main(): Promise<void> {
  const config = loadConfig();
  const logger = new Logger(parseLogLevel(config.logLevel));
  const java = new JavaApiClient(config, logger);
  const hil = new HilService(logger, config.orchestrator.hilWaitTimeoutMs);
  const metrics = new Metrics();
  const tasks = new TaskService({ config, logger, java, hil, metrics });

  // 启动对账（A9/A10）：把上一次进程遗留的 running/pending/awaiting_human 任务收敛为 interrupted，
  // 避免重启后任务永久悬挂在中间状态；需要继续时可由业务侧调用 rollback 从检查点续跑。
  const reconciled = await tasks.reconcileOnStartup();
  if (reconciled.recovered.length > 0) {
    logger.warn('启动对账完成：已收敛悬挂任务', {
      scanned: reconciled.scanned,
      recovered: reconciled.recovered.length,
    });
  }

  // B1/B5 启动期提示：鉴权与对外文件地址属于部署强相关配置
  if (config.auth.keys.length === 0) {
    if (config.auth.required) {
      logger.error('已开启 AGENT_REQUIRE_AUTH 但未配置 AGENT_API_KEYS：所有受保护接口都会返回 401');
    } else {
      logger.warn('未配置 AGENT_API_KEYS，接口鉴权为关闭状态（建议仅在本机开发时如此运行）');
    }
  }
  if (!config.publicBaseUrl) {
    logger.warn('未配置 AGENT_PUBLIC_BASE_URL，入库文件地址按本机端口推断（Java 与 Agent 不同机/容器时需显式配置）');
  }
  if (config.java.adminUsername === 'admin' && config.java.adminPassword === 'admin123') {
    logger.warn('知识库入库仍在使用内置默认账号 admin/admin123，生产环境请用 JAVA_ADMIN_USER/JAVA_ADMIN_PASS 覆盖');
  }
  checkTemplateDictConsistency(config, logger);

  const server = createHttpServer({ config, logger, tasks, metrics });

  server.listen(config.port, config.host, () => {
    logger.info('bid-agent-service 已启动', {
      host: config.host,
      port: config.port,
      backend: config.sessionBackend,
      java: config.java.baseUrl,
      llm: config.llm.baseUrl,
      workspace: config.paths.workspace,
      sessions: config.paths.sessions,
    });
  });

  let closing = false;
  const shutdown = async (signal: string): Promise<void> => {
    if (closing) return;
    closing = true;
    logger.info('收到退出信号，开始优雅停机', { signal });
    await tasks.shutdown('服务关闭，任务中断');
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(1), 5000).unref();
  };

  for (const signal of ['SIGINT', 'SIGTERM'] as const) {
    process.on(signal, () => {
      void shutdown(signal);
    });
  }

  process.on('unhandledRejection', (reason) => {
    const error = normalizeError(reason);
    logger.error('未处理的 Promise 拒绝，进程退出（交由容器/守护重启）', { message: error.message, category: error.category });
    process.exit(1);
  });

  process.on('uncaughtException', (error) => {
    const normalized = error instanceof AppError ? error : normalizeError(error);
    logger.error('未捕获异常，进程退出（交由容器/守护重启）', { message: normalized.message, stack: error.stack });
    process.exit(1);
  });
}

void main().catch((error) => {
  // 配置错误/端口占用等启动失败：显式退出，避免 unhandledRejection 只记日志后服务静默不监听端口
  const normalized = normalizeError(error);
  console.error('[bid-agent-service] 启动失败：', normalized.message);
  process.exit(1);
});
