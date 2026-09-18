package cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch;

// 规范依据：用户口径 2026-09-10「必须增加严格的监控指标：下载速度监测、文件完整性与状态监测」；
//   《招投标模块开发规范》红线 8（file_id + sha256 + size 三件套）。
// 业务目标：每一次"下载公告原文文件"都留一条可审计记录——下载量、耗时、速率、sha256、
//   完整性结论、归档（存储）状态，使"带宽是否异常""文件是否完整""是否真的存下来了"三问都有数据可答。
// 为什么用独立表而不是塞进 task.message：监控要能按时间/结论/源聚合查询（台账），
//   且 task 行会被重试覆盖，审计记录必须只追加、不可变。

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 原文下载审计（G 轨，表 bidding_crawl_download_audit）。
 */
@TenantIgnore // G 轨：表无 tenant_id 列（平台级采集数据）
@TableName("bidding_crawl_download_audit")
@Data
@EqualsAndHashCode(callSuper = true)
public class CrawlDownloadAuditDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 采集任务 id（列表直采/同步路径可能为 null） */
    private Long taskId;
    /** 采集源 code */
    private String sourceCode;
    /** 下载地址 */
    private String url;
    /** 下载类型：DETAIL_HTML / PDF / ARCHIVE_HTML / ATTACHMENT / CHANNEL */
    private String downloadKind;
    /** HTTP 状态码（本地失败为 null） */
    private Integer httpStatus;
    /** 下载字节数 */
    private Long byteSize;
    /** 耗时（毫秒） */
    private Long durationMs;
    /** 下载速率（字节/秒；= byteSize*1000/durationMs） */
    private Long speedBps;
    /** 内容 SHA-256（红线 8 三件套之一） */
    private String sha256;
    /** 完整性结论：PASS / FAIL_EMPTY / FAIL_TOO_LARGE / FAIL_FORMAT / FAIL_TEXT_TOO_SHORT / FAIL_ERROR_PAGE / FAIL_MOJIBAKE */
    private String integrityVerdict;
    /** 存储状态：NONE（未归档）/ REGISTERED（已归档且校验通过）/ FAILED（归档失败或校验不符） */
    private String storageStatus;
    /** 归档后的平台文件 id */
    private Long fileId;
    /** 备注/失败原因 */
    private String message;

}
