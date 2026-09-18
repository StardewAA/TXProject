package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工培训记录出参。
 *
 * <p>规范出处：用户业务口径（培训记录纳入员工资质管理）；《电子证件全流程管理_数据库设计.md》v3.3 表 15-3
 * （继续教育学时）。
 * <p>业务目标：员工资质全景中展示培训与继续教育履历。
 */
@Schema(description = "管理后台 - 员工培训记录出参")
@Data
public class PersonnelTrainingRespVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "归属企业")
    private Long entId;
    @Schema(description = "参训员工")
    private Long personId;
    @Schema(description = "培训名称")
    private String trainingName;
    @Schema(description = "培训类型 code")
    private String trainingType;
    @Schema(description = "培训机构")
    private String organizer;
    @Schema(description = "开始日期")
    private LocalDate startDate;
    @Schema(description = "结束日期")
    private LocalDate endDate;
    @Schema(description = "学时")
    private BigDecimal eduHours;
    @Schema(description = "结果 code")
    private String result;
    @Schema(description = "考核成绩")
    private BigDecimal score;
    @Schema(description = "培训合格证编号")
    private String certNo;
    @Schema(description = "培训合格证有效期止")
    private LocalDate certValidTo;
    @Schema(description = "证明文件 id")
    private Long fileId;
    @Schema(description = "备注")
    private String remark;
}
