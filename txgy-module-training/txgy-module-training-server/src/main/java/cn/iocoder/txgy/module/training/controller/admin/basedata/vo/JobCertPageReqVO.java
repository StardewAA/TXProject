package cn.iocoder.txgy.module.training.controller.admin.basedata.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 工种资格证分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class JobCertPageReqVO extends PageParam {

    @Schema(description = "工种编码（关联岗位工种）", example = "OP-02")
    private String jobCode;

    @Schema(description = "工种名称（模糊）", example = "防腐涂装工")
    private String jobName;

    @Schema(description = "资格证名称（模糊）", example = "特种作业操作证")
    private String certName;

    @Schema(description = "是否必须持证上岗", example = "true")
    private Boolean isRequired;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime[] createTime;

}
