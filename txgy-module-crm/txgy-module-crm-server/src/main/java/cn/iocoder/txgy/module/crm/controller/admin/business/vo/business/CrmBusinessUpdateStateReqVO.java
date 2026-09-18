package cn.iocoder.txgy.module.crm.controller.admin.business.vo.business;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.crm.enums.business.CrmBusinessStateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - CRM 商机状态组调整 Request VO（仅管理员）
 *
 * <p>商机状态组为只读派生字段，普通销售无权修改。
 * 本接口仅用于管理员「重新开启」（已赢单/已丢单 → 未开始/进行中）或纠正开启中的状态组；
 * 关闭商机必须走「赢单」「输单」按钮，以保证签约信息/丢单原因完整。
 *
 * @author 防腐保温项目组
 */
@Schema(description = "管理后台 - CRM 商机状态组调整 Request VO")
@Data
public class CrmBusinessUpdateStateReqVO {

    @Schema(description = "商机编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "32129")
    @NotNull(message = "商机编号不能为空")
    private Long id;

    @Schema(description = "目标状态组：1未开始 2进行中（不允许直接设置 3已赢单 / 4已丢单）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "商机状态组不能为空")
    @InEnum(CrmBusinessStateEnum.class)
    private Integer businessState;

    @Schema(description = "调整说明（会写入操作日志）", example = "赢单操作有误，重新开启")
    @Size(max = 500, message = "调整说明长度不能超过 500 个字符")
    private String reason;

}
