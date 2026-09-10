package cn.iocoder.txgy.module.bidding.controller.admin.project.vo;

// [规范依据]《招投标模块开发规范》§3.2（入参用 ReqVO）+ 数据规范 V2.0 数据集 ProjectVO
// [业务目标] 项目登记/修改：主列供列表与追溯，payload 落数据集全量字段（缺失自动补 null）
// [为什么] 主列=业务最常用检索项（编号/名称/地区/行业/投资代码），payload=权威全集（报送与第三方对接口径），二者分离免于列表查询碰大 JSON

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 项目 新建/修改 Request")
@Data
public class ProjectSaveReqVO {

    @Schema(description = "项目 ID；null=新建，非 null=修改")
    private Long id;

    @Schema(description = "项目编号（新建必填；租户内唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "E610000000000001001001")
    @NotBlank(message = "项目编号不能为空")
    private String projectCode;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "某输气管线防腐保温工程")
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    @Schema(description = "行政区划代码（GB/T 2260）")
    private String regionCode;
    @Schema(description = "投资项目统一代码（INVEST_PROJECT_CODE）")
    private String investProjectCode;
    @Schema(description = "关联项目编号（RELATE_CODE）")
    private String relateCode;
    @Schema(description = "建设地点（ADDRESS）")
    private String address;
    @Schema(description = "法定代表人（LEGAL_PERSON）")
    private String legalPerson;
    @Schema(description = "行业类别（INDUSTRIES_TYPE）")
    private String industriesType;
    @Schema(description = "资金来源（FUND_SOURCE）")
    private String fundSource;
    @Schema(description = "出资比例（CONTRIBUTION_SCALE）")
    private String contributionScale;
    @Schema(description = "项目规模（PROJECT_SCALE）")
    private String projectScale;
    @Schema(description = "审批部门名称（APPROVAL_DEPT_NAME）")
    private String approvalDeptName;
    @Schema(description = "审批部门代码（APPROVAL_DEPT_CODE）")
    private String approvalDeptCode;
    @Schema(description = "审批文号（APPROVAL_DOC_NO）")
    private String approvalDocNo;
    @Schema(description = "内部管理态：ACTIVE/CLOSED（默认 ACTIVE）")
    private String state;
    @Schema(description = "数据集字段（key=规范英文名，如 INVEST_PROJECT_CODE；服务端按 ProjectVO 规整并补 null）")
    private Map<String, Object> payload;
    @Schema(description = "备注")
    private String remark;
}
