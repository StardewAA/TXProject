package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleTicketPriorityEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleTicketSourceEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 售后问题工单 新增/修改 Request VO")
@Data
public class CrmAfterSaleTicketSaveReqVO {

    @Schema(description = "编号，主键自增（更新时必填）", example = "1")
    private Long id;

    @Schema(description = "问题标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "涂层局部起泡")
    @NotBlank(message = "问题标题不能为空")
    @Size(max = 200, message = "问题标题长度不能超过 200 个字符")
    private String title;

    @Schema(description = "关联质保档案编号；客户报障可为空", example = "1")
    private Long warrantyId;

    @Schema(description = "客户编号；为空时由质保档案/来源回访记录带出", example = "12")
    private Long customerId;

    @Schema(description = "合同编号；为空时由质保档案/来源回访记录带出", example = "15")
    private Long contractId;

    @Schema(description = "来源回访记录编号；客户报障为空", example = "1")
    private Long visitRecordId;

    @Schema(description = "工单来源：1=回访发现 2=客户报障；为空默认客户报障", example = "2")
    @InEnum(CrmAfterSaleTicketSourceEnum.class)
    private Integer source;

    @Schema(description = "优先级：1=高 2=中 3=低；为空默认中", example = "2")
    @InEnum(CrmAfterSaleTicketPriorityEnum.class)
    private Integer priority;

    @Schema(description = "问题类型（字典 crm_after_sale_problem_type）", example = "涂层脱落")
    private String problemType;

    @Schema(description = "问题描述", example = "罐体西侧发现约 0.5 平米起泡")
    @Size(max = 2000, message = "问题描述长度不能超过 2000 个字符")
    private String description;

    @Schema(description = "报障联系人姓名（快照）", example = "王工")
    @Size(max = 64, message = "报障联系人姓名长度不能超过 64 个字符")
    private String contactName;

    @Schema(description = "报障联系人电话（快照）", example = "13800000000")
    @Size(max = 20, message = "报障联系人电话长度不能超过 20 个字符")
    private String contactMobile;

    @Schema(description = "处理人（售后工程师/外协）；为空可稍后指派", example = "1")
    private Long assigneeUserId;

    @Schema(description = "备注", example = "客户比较着急，请优先处理")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}
