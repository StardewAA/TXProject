package cn.iocoder.txgy.module.cms.dto.material;

import cn.iocoder.txgy.module.cms.dto.common.CmsLibrarySearchReqDTO;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 选型候选筛选条件，不能代替工程选型结论。温度单位统一为摄氏度。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsMaterialSearchReqDTO extends CmsLibrarySearchReqDTO {
    @Size(max = 64)
    private String materialType;
    @Size(max = 64)
    private String mediumCode;
    @Size(max = 64)
    private String substrateCode;
    @Size(max = 64)
    private String fireRating;
    /** 要求材料 temp_min <= requiredTempMin；为空则不加此约束。 */
    @DecimalMin("-273.15")
    private BigDecimal requiredTempMin;
    /** 要求材料 temp_max >= requiredTempMax；为空则不加此约束。 */
    @DecimalMin("-273.15")
    private BigDecimal requiredTempMax;

    @AssertTrue(message = "工况最低温度不能高于最高温度")
    public boolean isTemperatureRangeValid() {
        return requiredTempMin == null || requiredTempMax == null
                || requiredTempMin.compareTo(requiredTempMax) <= 0;
    }
}
