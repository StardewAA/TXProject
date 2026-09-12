package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleWarrantyStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 售后质保档案 新增/修改 Request VO")
@Data
public class CrmAfterSaleWarrantySaveReqVO {

    @Schema(description = "编号，主键自增（更新时必填）", example = "1")
    private Long id;

    @Schema(description = "来源合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "15")
    @NotNull(message = "来源合同编号不能为空")
    private Long contractId;

    @Schema(description = "客户编号（由合同带出，无需传）", example = "12")
    private Long customerId;

    @Schema(description = "质保标的-合同产品行编号", example = "25")
    private Long contractProductId;

    @Schema(description = "工程项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "某储罐区防腐项目")
    @NotBlank(message = "工程项目名称不能为空")
    @Size(max = 128, message = "工程项目名称长度不能超过 128 个字符")
    private String projectName;

    @Schema(description = "项目完工验收日期（质保起算日）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "项目完工验收日期不能为空")
    private LocalDate projectAcceptDate;

    @Schema(description = "质保年限，1-5 年", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "质保年限不能为空")
    @Min(value = 1, message = "质保年限不正确，仅支持 1-5 年")
    @Max(value = 5, message = "质保年限不正确，仅支持 1-5 年")
    private Integer warrantyYears;

    @Schema(description = "质保到期日期；为空则自动计算=验收日期+年限-1天", example = "2028-09-06")
    private LocalDate warrantyEndDate;

    @Schema(description = "质保范围", example = "罐体外表面防腐涂层，质保期内的起泡/脱落免费维修")
    @Size(max = 500, message = "质保范围长度不能超过 500 个字符")
    private String warrantyScope;

    @Schema(description = "质保状态：1=质保中 2=即将到期 3=已到期终止", example = "1")
    @InEnum(CrmAfterSaleWarrantyStatusEnum.class)
    private Integer status;

    @Schema(description = "业务负责人（原合同业务员）；为空默认取合同负责人")
    private Long ownerUserId;

    @Schema(description = "售后责任人；为空默认当前登录用户")
    private Long serviceUserId;

    @Schema(description = "备注", example = "涉及二期扩容项目，重点关注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}
