package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 安全生产许可证分页查询入参（页签 4）。
 *
 * <p>规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G5/G6/G7/G8；《招投标模块开发规范》§4.1（分页口径）。
 * <p>为什么这样写：entId 必填 + 服务层兜底（防止直接调用服务方退化成全租户查询，错误码 1090109027）；
 * 其余筛选项可空，列表页默认按主体看全量；筛选项全部下推 SQL，保证分页 total 真实。
 * <p>业务目标：一屏数据一次取回，翻页不重不漏。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationPermitPageReqVO extends PageParam {

    @Schema(description = "企业主体 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "企业主体不能为空")
    private Long entId;
    @Schema(description = "许可状态 code（字典 safety_permit_status）", example = "VALID")
    private String permitStatus;

    @Schema(description = "只看有效期内且距到期 ≤ N 天（空=不过滤）", example = "180")
    private Integer expiringInDays;

    @Schema(description = "关键字：许可证编号 / 发证机关 模糊", example = "陕")
    private String keyword;
}
