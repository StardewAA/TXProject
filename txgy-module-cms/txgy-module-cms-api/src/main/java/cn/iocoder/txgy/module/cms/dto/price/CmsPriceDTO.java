package cn.iocoder.txgy.module.cms.dto.price;

import cn.iocoder.txgy.module.cms.dto.common.CmsResourceDTO;
import cn.iocoder.txgy.module.cms.dto.quota.CmsQuotaDTO.ResourceType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 某来源、地区、日期、规格和税费口径的价格快照，不宣称实时市场价。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsPriceDTO extends CmsResourceDTO {
    private String resourceId;
    private ResourceType resourceType;
    private String specification;
    private String regionCode;
    private LocalDate priceDate;
    private String unit;
    private String currency;
    private BigDecimal amount;
    private PriceType priceType;
    /** null 表示来源未说明；不能默认为不含税。 */
    private Boolean taxIncluded;
    /** 比例值，例如 0.13；未知时保持 null。 */
    private BigDecimal taxRate;
    private String feeScope;
    private String supplier;

    public enum PriceType { INFORMATION, SUPPLIER_QUOTE, TRANSACTION }
}
