package cn.iocoder.txgy.module.bidding.dal.dataobject.notice;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公告/公示主档（T 轨）。表：bidding_notice（DDL 见 sql/2026-09-03-notice.sql）。
 *
 * <p>权威字段全集以 {@code fieldsJson} 快照存储（key 照录规范英文名称，见 NoticePayloadUtils），
 * 本表只承载检索/列表/状态所需列 + 版式文件（PDF）指针。
 */
@TableName("bidding_notice")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 公告内部编号（bidding 域内唯一） */
    private String noticeCode;
    /** 公告/公示类型 code（NoticeTypeEnum） */
    private String noticeType;
    /** 公告/公示名称（标题） */
    private String title;
    /** 关联招标项目编号（如 UNIFIED_DEAL_CODE，可为空=不绑定项目） */
    private String projectCode;
    /** 招标项目名称快照（检索/列表展示免联表） */
    private String projectName;
    /** 发布单位名称（名称快照列，登记时点取值） */
    private String publisherName;
    /** 公告/公示发布时间（列表排序与大厅可见性依据） */
    private LocalDateTime publishTime;
    /** 状态：DRAFT / PUBLISHED / UNPUBLISHED / ARCHIVED（NoticeStatusEnum） */
    private String status;
    /** 1=企业内部公告（仅本租户可见）；0=面向公众（经公开白名单外发） */
    private Boolean internalFlag;
    /** 公开级别：SOCIAL / GOVERNMENT（对齐 PublicLevelEnum） */
    private String publicLevel;
    /** 权威数据集字段全集快照（JSON，key=规范英文名称，含 null 补齐） */
    private String fieldsJson;
    /** 版式文件（PDF）file_id（FileApi 对象主键；无则 null） */
    private Long pdfFileId;
    /** 版式文件（PDF）原文件哈希（SHA-256，红线 8 口径） */
    private String pdfSha256;
    /** 版式文件（PDF）原文件大小（字节） */
    private Long pdfSize;
    /** 乐观锁版本 */
    @Version
    private Integer version;

}
