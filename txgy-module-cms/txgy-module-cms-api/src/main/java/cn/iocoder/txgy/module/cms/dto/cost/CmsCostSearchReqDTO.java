package cn.iocoder.txgy.module.cms.dto.cost;

import cn.iocoder.txgy.module.cms.dto.common.CmsLibrarySearchReqDTO;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class CmsCostSearchReqDTO extends CmsLibrarySearchReqDTO {
    @Size(max = 100)
    private String edition;
    /** 资质/定额用于有效时点筛选，价格用于截至日期筛选，不混用其语义。 */
    private LocalDate asOfDate;
    @Size(max = 100)
    private String resourceId;
}
