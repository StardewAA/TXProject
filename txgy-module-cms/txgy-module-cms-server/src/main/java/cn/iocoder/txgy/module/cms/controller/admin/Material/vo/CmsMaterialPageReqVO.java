package cn.iocoder.txgy.module.cms.controller.admin.Material.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 材料参数数据库分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsMaterialPageReqVO extends PageParam {

    @Schema(description = "材料名称")
    private String materialName;
    @Schema(description = "子类")
    private String materialSubcategory;
    @Schema(description = "大类：1无机保温 2有机保温 3防腐涂料 4衬里材料 5防腐管件 6预制制品")
    private Integer materialCategory;
    @Schema(description = "燃烧等级")
    private String combustionGrade;
    @Schema(description = "审核状态")
    private Integer auditStatus;
    @Schema(description = "发布状态")
    private Integer publishStatus;
}
