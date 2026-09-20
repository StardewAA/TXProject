package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 管理后台 - 质保档案更新响应 VO
 *
 * <p>编辑质保档案时，「完工验收日期 / 质保年限 / 质保到期日期」任一变化都会触发
 * 质保状态重新计算。本 VO 把重算结果回传给前端，用于：
 * <ul>
 *   <li>保存后立即提示「状态已自动更新为 XX」，用户无需再手动点刷新；</li>
 *   <li>状态未变化时（changed=false）给出中性文案，避免误报；</li>
 *   <li>从「已到期终止」回退到有效状态时，提示关联回访计划已停用、需人工确认是否恢复。</li>
 * </ul>
 *
 * @author txgycloud
 */
@Schema(description = "管理后台 - 质保档案更新响应 VO")
@Data
@Accessors(chain = true)
public class CrmAfterSaleWarrantyUpdateRespVO {

    @Schema(description = "本次保存是否触发了质保状态变更", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean statusChanged;

    @Schema(description = "变更前的质保状态：1质保中 2即将到期 3已到期终止；未变更时与原状态相同", example = "1")
    private Integer oldStatus;

    @Schema(description = "变更后的质保状态（最终落库值）：1质保中 2即将到期 3已到期终止", example = "3")
    private Integer status;

    @Schema(description = "质保到期日期（重算后的最终值；留空时由验收日期 + 年限自动得出）", example = "2028-09-06")
    private java.time.LocalDate warrantyEndDate;

    @Schema(description = "状态变更说明；未变更时为中性提示", example = "保存成功，质保状态已自动更新为「已到期终止」")
    private String message;

    @Schema(description = "是否需要人工确认：状态由「已到期终止」回退为有效状态时，此前已停用的回访计划不会自动恢复", example = "false")
    private Boolean needManualConfirm;

}
