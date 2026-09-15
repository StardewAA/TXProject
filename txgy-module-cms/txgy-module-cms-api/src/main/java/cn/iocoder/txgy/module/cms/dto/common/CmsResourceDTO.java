package cn.iocoder.txgy.module.cms.dto.common;

import cn.iocoder.txgy.module.cms.enums.CmsPublishStatusEnum;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 资料只读模型，不是新增/修改请求，也不是数据库实体。
 * 租户、权限和发布状态必须由业务层校验，不能通过接收此 DTO 直接写库。
 */
@Data
public abstract class CmsResourceDTO {
    private Long id;
    private String title;
    /** 业务版本，如标准年代号或产品技术手册版本。 */
    private String version;
    /** 单调递增的记录修订号，用于同步幂等与防止乱序覆盖。 */
    private Long revision;
    private CmsPublishStatusEnum publishStatus;
    private Visibility visibility;
    private Source source;
    private List<String> industryCodes = List.of();
    private List<String> tags = List.of();
    /** 附件由现有文件服务管理，下载时仍须鉴权。 */
    private List<Long> attachmentIds = List.of();
    /** 投影到 Redis NUMERIC 字段时，统一转换为 Unix 毫秒。 */
    private Instant updatedAt;

    public enum Visibility { TENANT_ONLY, PUBLIC }

    /** 来源和授权描述不等于已取得授权，发布前须审核。 */
    public record Source(String sourceId, String publisher, String url,
                         String licenseNote, Instant collectedAt, Instant sourceUpdatedAt) { }
}
