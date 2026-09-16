package cn.iocoder.txgy.module.cms.dto.quota;

import cn.iocoder.txgy.module.cms.dto.common.CmsResourceDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 定额保存单位工程量对应的资源消耗量，价格另存 CmsPriceDTO。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsQuotaDTO extends CmsResourceDTO {
    private String quotaCode;
    private String edition;
    private String regionCode;
    private String measurementUnit;
    private String workDescription;
    private List<ResourceConsumption> consumptions = List.of();
    private List<Long> standardVersionIds = List.of();
    private LocalDate effectiveDate;
    private LocalDate expiryDate;

    public enum ResourceType { LABOR, MATERIAL, MACHINE }
    /** quantity 表示每个定额计量单位的资源消耗，不是货币金额。 */
    public record ResourceConsumption(String resourceId, String name, ResourceType resourceType,
                                      BigDecimal quantity, String unit) { }
}
