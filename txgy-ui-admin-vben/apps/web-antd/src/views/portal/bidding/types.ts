/**
 * 招投标门户（外部公告域 notice-external）接口类型
 *
 * 字段定义对齐 docs/外部公告-前端对接/01-接口契约.md + 后端 VO（NoticeExternalRespVO /
 * NoticeExternalDetailRespVO / NoticeExternalPageReqVO），一个字段都不能少；
 * 后端联调清单 R1~R12 均已实现：列表行已带 sourceCode/legalFlag（R2）、
 * noticeTypeRaw 精确筛选已生效（R1）、keyword 走 ES 全文检索（R5）。
 */

/** 病毒扫描状态（附件） */
export type VirusStatus = 'PENDING' | 'CLEAN' | 'INFECTED';

/**
 * 防腐保温行业判定（三态全值域；页面只高亮“相关/未知”，NOT_BELONGS 默认不标）：
 * BELONGS（防腐保温相关）/ NOT_BELONGS / UNCERTAIN（信息不足）
 */
export type IndustryFlag = 'BELONGS' | 'NOT_BELONGS' | 'UNCERTAIN';

/** 公告内容形态：HTML/PDF/ATTACHMENT/IMAGE/OTHER */
export type NoticeContentType = 'HTML' | 'PDF' | 'ATTACHMENT' | 'IMAGE' | 'OTHER';

/** 公告附件（下载地址=FileApi 签名；origDownloadUrl 为外站原始链接，仅溯源展示） */
export interface NoticeAttachment {
  id: number | string;
  name: string;
  ext?: string;
  fileId?: number | string;
  sha256?: string;
  size?: number;
  downloadUrl?: string;
  origDownloadUrl?: string;
  virusStatus?: VirusStatus;
}

/**
 * 正文结构化块（detail.bodyBlocksJson 数组元素，schema 见 02-字段枚举与展示约定 §4）。
 * 后端 @JsonInclude(NON_NULL)：无值字段缺省，按 type 读取对应载荷即可。
 */
export type NoticeBodyBlock =
  | { type: 'heading'; level: number; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'table'; caption?: string; headers?: string[]; rows: string[][] }
  | { type: 'list'; ordered: boolean; items: string[] };

/** 列表行（page 接口返回元素 = NoticeExternalRespVO） */
export interface NoticeExternalItem {
  id: number | string;
  /** 来源站 code（列表已回传，R2 完成） */
  sourceCode?: string;
  sourceName?: string;
  title: string;
  /** 原网址（大厅展示必带；外链一律走后端 /go 中转） */
  origUrl?: string | null;
  /** 内容形态：HTML/PDF/ATTACHMENT/IMAGE/OTHER */
  contentType?: NoticeContentType | string | null;
  /** 公告性质（外站自述原文）；noticeTypeRaw 精确筛选入参（eq，R1 完成） */
  noticeTypeRaw?: string | null;
  /** 依法必招：Y/N（NULL=未知） */
  legalFlag?: string | null;
  industryFlag?: IndustryFlag | string | null;
  issuerName?: string | null;
  /** 关联项目跟踪键（BR-1：page/detail 行均下发；采集回传存在 → 详情可“跟踪该项目”次级关注） */
  projectKey?: string | null;
  /** 项目名称快照（与 projectKey 并列下发；一期外部采集可为 null） */
  projectName?: string | null;
  /** 招标项目跟踪键（一期恒 null：外部采集无招标项目编码数据源） */
  tenderProjectKey?: string | null;
  /** 招标项目名称（一期恒 null） */
  tenderProjectName?: string | null;
  /** 标段跟踪键列表（bidSectionCodes 按 ,; 拆分；一期恒 []/null） */
  bidSectionKeys?: string[] | null;
  /** 标段名称列表（一期恒 []/null） */
  bidSectionNames?: string[] | null;
  /** 标段原文（兼容保留；已由 bidSectionKeys 归一拆分） */
  bidSectionCodes?: string[] | string | null;
  /** 后端出参为 epoch 毫秒；mock 兼容字符串（0/空视为无值） */
  publishTime?: number | string | null;
  effectiveFrom?: number | string | null;
  effectiveTo?: number | string | null;
  /** 大厅只读口径恒为 ACTIVE（02 §3.4：前端不传状态筛选） */
  status?: string | null;
}

/** 详情（= NoticeExternalDetailRespVO：列表字段 + 正文与文件） */
export interface NoticeExternalDetail extends NoticeExternalItem {
  /** HTML 原样（留档不下发公众，公开出参恒为 null；渲染一律用 contentHtmlSafe / bodyBlocks） */
  contentHtml?: string | null;
  /** HTML 净化后内容（后端 jsoup 白名单结构化净化，仅 contentType=HTML） */
  contentHtmlSafe?: string | null;
  pdfFileId?: number | string | null;
  pdfSha256?: string | null;
  pdfSize?: number | null;
  /** PDF 下载地址（FileApi 签名；签名能力接入前为 null，R6） */
  pdfDownloadUrl?: string | null;
  extractionStatus?: string | null;
  /** 正文结构化策略码：HTML_STRUCTURE / AI_FALLBACK（null=未结构化） */
  bodyParserCode?: string | null;
  /** 正文结构化状态：NONE（默认）/ SUCCESS / FAILED */
  bodyBlocksStatus?: string | null;
  /** 正文结构化块原始 JSON 串（SUCCESS 时非空；解析结果见 bodyBlocks） */
  bodyBlocksJson?: string | null;
  /** 正文结构化块（前端已解析为数组；SUCCESS 且非空时按块渲染，优先于 contentHtmlSafe，见 01 §7.1） */
  bodyBlocks?: NoticeBodyBlock[];
  /** 可为 null（演示库即 null），api 层已归一化为数组 */
  attachments?: NoticeAttachment[] | null;
}

/** page 请求参数（= NoticeExternalPageReqVO；大厅只读口径不传 status） */
export interface NoticePageParams {
  /** 关键词：标题/项目名/编号/正文全文检索，走 ES（bidding-notice-external，R5；需后端 bidding.es.enabled=true） */
  keyword?: string;
  /** 标题精确匹配筛选（契约原始口径；前缀/模糊检索一律走 keyword） */
  title?: string;
  /** 来源站 code（精确筛选；来源字典为数据驱动 bidding_crawl_source，R3） */
  sourceCode?: string;
  /** 内容形态：HTML/PDF/ATTACHMENT/IMAGE/OTHER */
  contentType?: string;
  /** 防腐保温行业判定：BELONGS/NOT_BELONGS/UNCERTAIN */
  industryFlag?: IndustryFlag | string;
  /** 公告类型（外站原文，精确匹配 eq；R1 已实现） */
  noticeTypeRaw?: string;
  /**
   * “中标公示”合并视图的原始值组（候选类+结果类，api 层按组抓取合并；仅 fetchNoticePage 内部使用，
   * 见 api.ts RESULT_TYPE_MERGE / classifyResultRaw，01 §6 R1）
   */
  resultRaws?: string[];
  /** 关联项目编号（跟踪维度，大厅不传） */
  projectCode?: string;
  /** 发布时间区间起（毫秒或 'YYYY-MM-DD HH:mm:ss'，由调用方归一） */
  beginTime?: string | number;
  /** 发布时间区间止 */
  endTime?: string | number;
  pageNo: number;
  pageSize: number;
}

/** page 响应（芋道 PageResult 风格：list/total） */
export interface PageResult<T> {
  list: T[];
  total: number;
}

/* ================= 关注 / 订阅域（notice-follow，01 §3 / 02 §5） ================= */

/** 关注目标类型：公告/项目/招标项目/标段 */
export type FollowTargetType = 'ANNOUNCEMENT' | 'PROJECT' | 'TENDER_PROJECT' | 'SECTION';

/** 关注入参（FollowReqVO：targetType+targetKey，幂等） */
export interface FollowReq {
  targetType: FollowTargetType | string;
  /** 目标键：公告=公告 id；项目/招标项目/标段=维度代码 */
  targetKey: string;
}

/** 我的关注列表行（FollowItemRespVO；筛选参数真名 followType，契约已勘误） */
export interface FollowItem {
  id: number | string;
  /** 维度：ANNOUNCEMENT/PROJECT/TENDER_PROJECT/SECTION（服务层对公告级关注也建行） */
  followType?: string | null;
  followKey?: string | null;
  /** 项目/标段/公告标题快照（公告级关注未扇出时后端暂存 targetKey，前端可回查 detail 补名） */
  displayName?: string | null;
  issuerName?: string | null;
  /** 进展阶段 code：PENDING/TENDER/OPEN_BID/EVAL/RESULT/CONTRACT/DONE/UNKNOWN */
  stageCode?: string | null;
  stageName?: string | null;
  /** 基础信息快照（JSON 串） */
  baseJson?: string | null;
  /** 最近动态时间（epoch 毫秒） */
  latestDynamicTime?: number | string | null;
  /** 未读动态数 */
  unreadCount?: number | null;
  /** 关注时间（epoch 毫秒；BR-6 起可显示“关注于 xx”） */
  followTime?: number | string | null;
  /** 关联公告展示状态（公告级关注=外部公告 ACTIVE/INACTIVE/…；维度行为 null，BR-4） */
  noticeStatus?: string | null;
  /** 平台详情可达性（false=公告已下线/归档，隐藏平台详情跳转；维度行/无关联公告为 null，BR-4） */
  available?: boolean | null;
}

/** 我的关注列表分页入参（FollowItemPageReqVO） */
export interface FollowItemPageParams {
  /** 维度过滤：PROJECT/TENDER_PROJECT/SECTION/ANNOUNCEMENT（空=全部） */
  followType?: string;
  /** 目标键精确过滤（公告 id / 维度代码；配合 followType，item/page 已支持，N2②） */
  followKey?: string;
  pageNo: number;
  pageSize: number;
}

/** 关注状态单点查询出参（FollowStatusRespVO：BR-2 / N2② 新增；详情页关注按钮初始态直接查询，无需逐页扫描） */
export interface FollowStatus {
  /** 是否已关注 */
  followed: boolean;
  /** 已关注时的关注行 id（可跳时间线） */
  itemId?: number | string | null;
  /** 目标类型（回显入参） */
  targetType?: string | null;
  /** 目标键（回显入参） */
  targetKey?: string | null;
  /** 名称快照（公告=公告标题/项目名；未快照为 null） */
  displayName?: string | null;
  /** 发布单位快照（可为 null） */
  issuerName?: string | null;
}

/** 关注项动态/事件（FollowEventRespVO，timeline 行） */
export interface FollowEvent {
  id: number | string;
  /** 来源：INTERNAL/EXTERNAL */
  source?: string | null;
  /** 事件类型：NOTICE_PUBLISHED/NOTICE_UPDATED/EXTERNAL_ACTIVE/STAGE_CHANGE */
  eventType?: string | null;
  /** 关联公告 id（可空；EXTERNAL_ACTIVE 指向外部公告） */
  noticeId?: number | string | null;
  titleSnapshot?: string | null;
  /** 原网址（外部公告） */
  origUrl?: string | null;
  /** 动态发生时间（epoch 毫秒） */
  occurredAt?: number | string | null;
  readFlag?: boolean | null;
  /** 关联公告展示状态（EXTERNAL=外部公告 ACTIVE/INACTIVE…；INTERNAL=内部公告 PUBLISHED/UNPUBLISHED…；无公告为 null，BR-4） */
  noticeStatus?: string | null;
  /** 平台详情可达性（false=公告已下线/归档，隐藏详情跳转；EXTERNAL 仍可经 origUrl 看源站原文；无公告为 null，BR-4） */
  available?: boolean | null;
}

/* ============ 内部公告（招标人自营 /bidding/notice public 域） ============ */
export interface InternalNoticeItem {
  id: number | string;
  noticeCode?: string;
  noticeType?: string;
  noticeTypeName?: string;
  title: string;
  projectCode?: string;
  projectName?: string;
  publisherName?: string;
  publishTime?: number | string;
  status?: string;
  publicLevel?: string;
  contentPreview?: string;
}
export interface InternalNoticeDetail extends InternalNoticeItem {
  content?: string;
  contentVersion?: number;
  payload?: Record<string, unknown>;
  pdfFileId?: number | string;
  pdfSize?: number;
}
export interface InternalNoticeParams {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  noticeType?: string;
}
