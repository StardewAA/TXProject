package cn.iocoder.txgy.module.cms.dto.qualification;

import cn.iocoder.txgy.module.cms.dto.common.CmsResourceDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/** 资质要求是政策资料，不是定额或价格；必须保留地区、来源和有效期间。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsQualificationDTO extends CmsResourceDTO {
    private String qualificationName;
    private String level;
    private String regionCode;
    private String authority;
    private String requirements;
    private String applicationProcess;
    private List<String> requiredDocuments = List.of();
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
}
