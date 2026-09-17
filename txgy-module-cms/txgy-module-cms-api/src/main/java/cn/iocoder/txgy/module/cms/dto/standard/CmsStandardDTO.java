package cn.iocoder.txgy.module.cms.dto.standard;

import cn.iocoder.txgy.module.cms.dto.common.CmsResourceDTO;
import cn.iocoder.txgy.module.cms.enums.CmsStandardStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/** 标准元数据与获授权正文；未授权时仅保留题录及官方链接。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsStandardDTO extends CmsResourceDTO {
    private String code;
    private String category;
    private String issuingAuthority;
    private String regionCode;
    private List<String> scenarioCodes = List.of();
    private String content;
    private LocalDate publishedDate;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private CmsStandardStatusEnum status;
    /** 指向标准具体版本，而非仅保存一个会被覆盖的编号。 */
    private List<Long> replacesIds = List.of();
    private List<Long> replacedByIds = List.of();
}
