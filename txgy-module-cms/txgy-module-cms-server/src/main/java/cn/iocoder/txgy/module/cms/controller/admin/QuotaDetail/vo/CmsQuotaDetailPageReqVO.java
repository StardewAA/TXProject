package cn.iocoder.txgy.module.cms.controller.admin.QuotaDetail.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Schema(description = "管理后台 - 定额明细表分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsQuotaDetailPageReqVO extends PageParam {

    @Schema(description = "项目名称")
    private String itemName;
    @Schema(description = "关联主表ID")
    private Long quotaId;
}
