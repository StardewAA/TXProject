package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 要素台账分页查询入参（人员配置 / 工程业绩 / 资产 三张子表共用）。
 *
 * <p>规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G5/G6/G7/G8；《招投标模块开发规范》§4.1（分页口径）。
 * <p>为什么这样写：entId 必填 + 服务层兜底（防止直接调用服务方退化成全租户查询，错误码 1090109027）；
 * 其余筛选项可空，列表页默认按主体看全量；筛选项全部下推 SQL，保证分页 total 真实。
 * <p>业务目标：一屏数据一次取回，翻页不重不漏。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationElementPageReqVO extends PageParam {

    @Schema(description = "企业主体 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "企业主体不能为空")
    private Long entId;
    @Schema(description = "资质主档 id（看某条资质下的要素时传）", example = "9001")
    private Long qualId;
}
