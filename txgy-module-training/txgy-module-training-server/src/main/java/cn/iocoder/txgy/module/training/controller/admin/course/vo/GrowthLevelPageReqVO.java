package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

@Schema(description = "管理后台 - 成长等级分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class GrowthLevelPageReqVO extends PageParam {

    @Schema(description = "等级名称（模糊）", example = "学徒")
    private String name;

    @Schema(description = "等级编码", example = "APPRENTICE")
    private String code;

    @Schema(description = "等级序号", example = "1")
    private Integer levelNo;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime[] createTime;

}
