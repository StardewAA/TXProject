package cn.iocoder.txgy.module.training.controller.admin.basedata.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 岗位工种分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class JobPageReqVO extends PageParam {

    @Schema(description = "工种名称（模糊）", example = "防腐涂装工")
    private String name;

    @Schema(description = "工种编码", example = "OP-02")
    private String code;

    @Schema(description = "序列编码：OP/TC/QC/MG/SP", example = "OP")
    private String seqCode;

    @Schema(description = "最高成长等级（关联成长等级字典）", example = "SENIOR_TECHNICIAN")
    private String maxLevel;

    @Schema(description = "状态：active 启用 / disabled 停用", example = "active")
    private String status;

    @Schema(description = "学习域/课程组编码（模糊匹配规范化编码）", example = "B01")
    private String learnDomainCode;

    @Schema(description = "关键技能（模糊）", example = "喷砂")
    private String keySkills;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime[] createTime;

}
