package cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal;
// 规范依据：设计文档 v0.6 §7/§15.1（G 轨聚合主档）；《招投标模块开发规范》五章（长度/索引/审计列）

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 外部公告主档（G 轨，平台级聚合，无租户语义）。表：bidding_notice_external。
 *
 * <p>职责：外部网站公告的“留档 + 对外只读展示”唯一载体；HTML 原样 / PDF（file_id+sha256+size）/
 * 附件三形态 + 原网址；动态字段全集落 meta_json / extra_raw 兜底；PDF 抽取结果落 extraction_* 列。
 * 去重键 origUrlHash；展示过滤：publicScope=SOCIAL 且 status=ACTIVE（01 规则 + 设计文档 §7）。
 */
@TableName("bidding_notice_external")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeExternalDO extends BaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 来源站配置 code（bidding_crawl_source.source_code） */
    private String sourceCode;
    /** 来源站名称快照 */
    private String sourceName;
    /** 标题（列表/检索核心） */
    private String title;
    /** 原网址（展示必带） */
    private String origUrl;
    /** URL SHA-256（去重指纹，唯一键） */
    private String origUrlHash;
    /** 正文/文件指纹（内容级去重/变更识别） */
    private String contentHash;
    /** 内容形态：HTML/PDF/ATTACHMENT/IMAGE/OTHER（NoticeExternalContentTypeEnum） */
    private String contentType;
    /** HTML 原文（原样保存，不转义不裁剪） */
    private String contentHtml;
    /** PDF 原文件 file_id（红线 8：file_id+sha256+size） */
    private Long pdfFileId;
    /** PDF 原文件哈希 */
    private String pdfSha256;
    /** PDF 原文件大小（字节） */
    private Long pdfSize;
    /** 附件记录 id 列表（bidding_notice_external_attachment，JSON 数组） */
    private String attachmentIdsJson;
    /** 外站自述类型原文（不猜不丢） */
    private String noticeTypeRaw;
    /** 可映射的权威码（映射不到为 NULL） */
    private String noticeTypeCode;
    /** 依法必招标记：Y/N/NULL=未知 */
    private String legalFlag;
    /** 防腐保温行业 AI 判定：BELONGS/NOT_BELONGS/UNCERTAIN */
    private String industryFlag;
    /** 行业判定置信度 0~1 */
    private BigDecimal industryConfidence;
    /** 关联项目编号（跟踪维度，可为空） */
    private String projectCode;
    /** 关联招标项目编号（跟踪维度） */
    private String tenderProjectCode;
    /** 关联标段编号（多个逗号分隔，跟踪维度） */
    private String bidSectionCodes;
    /** 项目名称快照（基础信息） */
    private String projectName;
    /** 发布单位（快照） */
    private String issuerName;
    /** 外站公告时间（解析失败取抓取时间） */
    private LocalDateTime publishTime;
    /** 生效开始 */
    private LocalDateTime effectiveFrom;
    /** 生效截止（判断是否在生效期） */
    private LocalDateTime effectiveTo;
    /** 状态：NEW→ACTIVE→INACTIVE→ARCHIVED（NoticeExternalStatusEnum） */
    private String status;
    /** 审核状态：UNREVIEWED/APPROVED/REJECTED/TAKEN_DOWN */
    private String auditStatus;
    /** 审批绑定/本次处理的策略码（AI 模板/规则 code） */
    private String parserCode;
    /** 公开范围（默认 SOCIAL 可对外展示，01 公开级别对齐） */
    private String publicScope;
    /** 抓取次数 */
    private Integer fetchCount;
    /** 最近抓取时间 */
    private LocalDateTime lastFetchTime;
    /** PDF 抽取状态：NONE/SUCCESS/FAIL_EMPTY/FAIL（OpenDataLoader 空结果=FAIL_EMPTY 直接失败） */
    private String extractionStatus;
    /** PDF 抽取的纯文本（OpenDataLoader 本地模式） */
    private String extractionText;
    /** PDF 抽取的表格（JSON 数组） */
    private String extractionTablesJson;
    /** PDF 抽取时间 */
    private LocalDateTime extractionAt;
    /** 原始页面结构化字段全集（JSON，兜底防丢） */
    private String metaJson;
    /** 整页快照（可追溯/复审） */
    private String extraRaw;

}
