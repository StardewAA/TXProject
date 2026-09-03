package cn.iocoder.txgy.module.cms.dto.standard;

import cn.iocoder.txgy.module.cms.dto.common.CmsLibrarySearchReqDTO;
import cn.iocoder.txgy.module.cms.enums.CmsStandardStatusEnum;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class CmsStandardSearchReqDTO extends CmsLibrarySearchReqDTO {
    /** 完整标准编号，包含年代号；精确查询时投影为 TAG。 */
    @Size(max = 100)
    private String code;
    @Size(max = 64)
    private String category;
    private CmsStandardStatusEnum status;
    /** 历史查询时点，不自动等同于当前有效。 */
    private LocalDate asOfDate;
}
