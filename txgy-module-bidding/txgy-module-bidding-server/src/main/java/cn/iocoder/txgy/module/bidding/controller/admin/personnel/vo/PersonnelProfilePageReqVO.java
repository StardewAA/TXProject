package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 员工档案分页查询入参。
 *
 * <p>规范出处：《招投标模块开发规范》§4.2/§4.4（分页用 PageParam、pageSize ≤ 100）、§4.6（禁 LIKE 全文检索，
 * 名称类模糊查询仅作辅助筛选，命中前缀索引）。
 * <p>业务目标：按企业/部门/在职状态/证照类型快速检索人员，替代 Excel 台账的人工筛选。
 */
@Schema(description = "管理后台 - 员工档案分页查询入参")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonnelProfilePageReqVO extends PageParam {

    @Schema(description = "归属企业", example = "1")
    private Long entId;

    @Schema(description = "员工编号（精确）", example = "E2026001")
    private String personNo;

    @Schema(description = "姓名（模糊）", example = "张")
    private String personName;

    @Schema(description = "所属部门（模糊）", example = "工程")
    private String deptName;

    @Schema(description = "在职状态 code：ACTIVE/LEAVE/SUSPENDED/RETIRED", example = "ACTIVE")
    private String personnelStatus;

    @Schema(description = "学历层次 code", example = "BACHELOR")
    private String eduLevel;

    @Schema(description = "职称等级 code", example = "MIDDLE")
    private String titleLevel;

    @Schema(description = "持有证书类型 code（按证书反查人员，如 REG_PRACTICE_CERT 查“有注册类证书的人”）",
            example = "REG_PRACTICE_CERT")
    private String certType;
}
