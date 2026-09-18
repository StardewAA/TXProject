package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 延续申报单分页查询入参（页签 2「延续申报」）。
 *
 * <p>规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G3；《招投标模块开发规范》§4.1 分页口径。
 *
 * <p>为什么这样写：① entId 必填（跨企业隔离）；② status 可空——页面按状态分泳道时
 * 各泳道各查一次，而"全部"视图不传 status；③ 不提供日期区间：申报单量级小，
 * 排序用 submitDate 倒序即可，避免参数膨胀且用不上的筛选项。
 *
 * <p>业务目标：审批人按泳道看待办与历史，企业侧看自己的申报进度。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationApplyPageReqVO extends PageParam {

    @Schema(description = "企业主体 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "企业主体不能为空")
    private Long entId;

    @Schema(description = "资质主档 id（看某条资质的申报历史时传）", example = "9001")
    private Long qualId;

    @Schema(description = "申报类型 code：RENEW 延续 / UPGRADE 升级 ...（当前仅 RENEW 支持）", example = "RENEW")
    private String applyType;

    @Schema(description = "申报状态 code：SUBMITTED 待批 / APPROVED 已批 / REJECTED 已退回 / ISSUED 已办结", example = "SUBMITTED")
    private String status;
}
