package cn.iocoder.txgy.module.wms.api.stockapplication.dto;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true)
public class WmsStockApplicationPageReqDTO extends PageParam {
    @Pattern(regexp = "OTHER_IN|OTHER_OUT|TRANSFER|CHECK") private String type;
    private String sourceSystem;
    private String sourceNo;
    private String approvalStatus;
    private String executionStatus;
}
