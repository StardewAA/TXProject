package cn.iocoder.txgy.module.cms.dto.material;

import cn.iocoder.txgy.module.cms.dto.common.CmsResourceDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CmsMaterialDTO extends CmsResourceDTO {
    private String name;
    private String materialType;
    private String brand;
    private String model;
    /** 摄氏度，使用两个 NUMERIC 字段，不使用数值数组表示区间。 */
    private BigDecimal tempMin;
    private BigDecimal tempMax;
    private String temperatureConditions;
    private List<String> applicable = List.of();
    private List<String> mediumCodes = List.of();
    private List<String> substrateCodes = List.of();
    private String fireRating;
    private List<Parameter> parameters = List.of();
    /** 优劣说明需关联可核验资料，不能由模型虚构。 */
    private List<String> advantages = List.of();
    private List<String> limitations = List.of();

    public record Parameter(String name, BigDecimal value, String unit,
                            String testConditions, String testMethod, String sourceUrl) { }
}
