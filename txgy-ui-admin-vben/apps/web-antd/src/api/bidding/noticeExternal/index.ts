import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 契约来源：01-接口逐一说明与设计依据 §2（外部公告域 G 轨）+ 04-全链路接口说明 §2 + 06-接口完全对照 §3；管理端审核/运营端点另见爬虫工作台契约 §1.4（审查留痕）。 */
export namespace NoticeExternalApi {
  /** 管理端行（NoticeExternalManageRespVO，01 §2 manage/page） */
  export interface ManageItem {
    /** 公告主键（雪花 ID，后端 Long 序列化为字符串时前端按 string 兼容） */
    id?: number | string;
    sourceCode?: string;
    sourceName?: string;
    title?: string;
    origUrl?: string;
    contentType?: string;
    noticeTypeRaw?: string;
    legalFlag?: string;
    industryFlag?: string;
    industryConfidence?: number;
    issuerName?: string;
    projectName?: string;
    bidSectionCodes?: string;
    status?: string;
    auditStatus?: string;
    extractionStatus?: string;
    aiExtractionStatus?: string;
    bodyBlocksStatus?: string;
    bodyParserCode?: string;
    publishTime?: string;
    effectiveFrom?: string;
    effectiveTo?: string;
    fetchCount?: number;
    lastFetchTime?: string;
    createTime?: string;
    updateTime?: string;
  }

  /** 管理详情（NoticeExternalManageDetailRespVO：BR-7 manage/get，全状态预览） */
  export interface ManageDetail extends ManageItem {
    publicScope?: string;
    contentHtmlSafe?: string;
    bodyBlocksJson?: string;
    pdfFileId?: number;
    pdfSha256?: string;
    pdfSize?: number;
    pdfDownloadUrl?: string;
    extractionSummary?: string;
    aiMetaFieldCount?: number;
    projectKey?: string;
    tenderProjectKey?: string;
    bidSectionKeys?: string[];
    attachments?: Array<{
      downloadUrl?: string;
      ext?: string;
      id?: number | string;
      name?: string;
      origDownloadUrl?: string;
      size?: number;
      virusStatus?: string;
    }>;
  }

  /**
   * 操作日志行（NoticeExternalOperateLogRespVO）：数据源=**平台 system_operate_log**（不另建表），
   * 用来回答"谁上传的、谁审查的、AI 什么时候审的"——AI 审查的理由与命中词就在 action / extra 里。
   * 由 NoticeOperateLogHelper（系统/AI 动作）与 @LogRecord（人工动作）写入。
   */
  export interface OperateLogItem {
    id?: number | string;
    /** 操作人编号（0=系统/AI：爬虫入库、AI 审查等非人工动作） */
    userId?: number | string;
    /** 操作人姓名（平台按 userId 翻译；系统/AI 动作为「系统/AI」） */
    userName?: string;
    /** 操作人类型：1=会员 2=管理员 */
    userType?: number;
    /** 操作名（如「爬虫采录入库」「AI 审查通过」「人工审核通过外部公告」） */
    subType?: string;
    /** 操作内容（人可读描述，AI 审查含理由与命中词） */
    action?: string;
    success?: boolean;
    /** 拓展字段（JSON：来源站/AI 结论等） */
    extra?: string;
    /** 请求方式（系统动作为 SYSTEM/AI 占位标识） */
    requestMethod?: string;
    /** 请求地址（系统动作为 bidding://... 伪地址） */
    requestUrl?: string;
    userIp?: string;
    /** 操作时间（LocalDateTime → epoch 毫秒；渲染走 data.ts 的 fmtT，兼容数字/13 位串/带空格串） */
    createTime?: string;
  }

  /** manage/page 筛选（结构化过滤；勿传 keyword，管理列表无 ES 全文） */
  export interface ManagePageParams extends PageParam {
    status?: string;
    auditStatus?: string;
    sourceCode?: string;
    contentType?: string;
    noticeTypeRaw?: string;
    industryFlag?: string;
    projectCode?: string;
    beginTime?: string;
    endTime?: string;
  }
}

/** 契约：GET /bidding/notice-external/manage/page —— 外部公告管理端分页（全状态 × 审核态；结构化过滤，勿传 keyword）；权限 bidding:notice-external:update；依据 04-全链路 §2.2 */
export function getManagePage(params: NoticeExternalApi.ManagePageParams) {
  return requestClient.get<PageResult<NoticeExternalApi.ManageItem>>(
    '/bidding/notice-external/manage/page',
    { params },
  );
}

/** 契约：GET /bidding/notice-external/manage/operate-log/page —— 外部公告操作日志分页（谁上传/谁审查/AI 何时审；数据来自平台 system_operate_log，不另建表）；权限 bidding:notice-external:update；依据 NoticeExternalController#operateLogPage */
export function getOperateLogPage(params: {
  /** 公告主键（必传；后端 @RequestParam noticeId） */
  noticeId: number | string;
  pageNo?: number;
  pageSize?: number;
}) {
  return requestClient.get<PageResult<NoticeExternalApi.OperateLogItem>>(
    '/bidding/notice-external/manage/operate-log/page',
    { params },
  );
}

/** 契约：GET /bidding/notice-external/manage/get —— 管理详情预览（BR-7：全状态净化正文/结构化块/PDF/附件病毒状态，支撑审核页读完正文再 approve）；权限 bidding:notice-external:update；依据 04-全链路 §2.11（BR-7） */
export function getManageDetail(id: number | string) {
  return requestClient.get<NoticeExternalApi.ManageDetail>(
    '/bidding/notice-external/manage/get',
    { params: { id } },
  );
}

/** 契约：POST /bidding/notice-external/approve —— 审核通过（NEW 转 ACTIVE，触发关注提醒扇出与 ES 索引事件，幂等）；权限 bidding:notice-external:update；依据 04-全链路 §2.5 / 爬虫工作台契约 §1.4 */
export function approveNotice(id: number | string) {
  return requestClient.post('/bidding/notice-external/approve', undefined, {
    params: { id },
  });
}

/** 契约：POST /bidding/notice-external/reject —— 审核驳回（不对外展示并留痕）；权限 bidding:notice-external:update；依据 04-全链路 §2.6 */
export function rejectNotice(id: number | string) {
  return requestClient.post('/bidding/notice-external/reject', undefined, {
    params: { id },
  });
}

/** 契约：POST /bidding/notice-external/take-down —— 下线外部公告（ACTIVE 转 INACTIVE，大厅展示随即消失）；权限 bidding:notice-external:update；依据 04-全链路 §2.7 */
export function takeDownNotice(id: number | string) {
  return requestClient.post('/bidding/notice-external/take-down', undefined, {
    params: { id },
  });
}

/** 契约：POST /bidding/notice-external/ai-extract —— AI 结构化抽取与防腐保温行业判定（回写 industryFlag/industryConfidence，人工触发）；权限 bidding:notice-external:update；依据 04-全链路 §2.9 */
export function aiExtractNotice(id: number | string) {
  return requestClient.post('/bidding/notice-external/ai-extract', undefined, {
    params: { id },
  });
}

/** 契约：POST /bidding/notice-external/body-structure —— 正文结构化（常规 HTML 解析或 AI 兜底排版生成 bodyBlocks 供渲染）；权限 bidding:notice-external:update；依据 04-全链路 §2.10 */
export function bodyStructureNotice(id: number | string) {
  return requestClient.post('/bidding/notice-external/body-structure', undefined, {
    params: { id },
  });
}

/** 契约：POST /bidding/notice-external/backfill-pdf-urls —— 存量外部公告 PDF 归档回填（重下载外站 PDF → infra-file 上传 → 回填 pdf_download_url 列）；
 * 业务/理由：爬取 PDF 经平台文件服务归档后才返回可访问 URL（pdf_download_url）；旧数据该列为空，需人工触发一次回填；
 * 权威判断：外部采集公告 pdf_file_id 恒 NULL，有否 PDF 看 contentType=='PDF'、访问一律用 pdf_download_url（勿再拼 pdf_file_id）。
 * 权限 bidding:notice-external:update；依据 外部公告-前端对接/04-PDF归档与源站跳转与采集窗口契约补充.md §1（存量回填，返回 total/ok/failed/skipped，约几十秒完成）。 */
export function backfillPdfUrls() {
  return requestClient.post<{ failed?: number; ok?: number; skipped?: number; total?: number; }>(
    '/bidding/notice-external/backfill-pdf-urls',
    undefined,
  );
}

/**
 * 重建外部公告检索索引（POST /bidding/notice-external/reindex；权限 bidding:notice-external:update）。
 * 返回后端结果文案；与 /ingest 不同，本端点无需入参，属于运维重跑动作。
 */
export function reindexNoticeExternalIndex() {
  return requestClient.post<string>('/bidding/notice-external/reindex');
}
