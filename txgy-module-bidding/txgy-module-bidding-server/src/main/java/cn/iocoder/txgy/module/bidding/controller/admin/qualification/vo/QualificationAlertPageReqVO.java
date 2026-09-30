package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资质到期预警清单分页查询入参（页签 4）。
 *
 * <p>规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G5/G6/G7/G8；《招投标模块开发规范》§4.1（分页口径）。
 * <p>为什么这样写：entId 可选（不传时由 Controller 从本租户主体档案推导）+ 服务层兜底
 * （防止直接调用服务方退化成全租户查询，错误码 1090109027）；
 * 其余筛选项可空，列表页默认按主体看全量；筛选项全部下推 SQL，保证分页 total 真实。
 * <p>业务目标：一屏数据一次取回，翻页不重不漏。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationAlertPageReqVO extends PageParam {

    @Schema(description = "企业主体 id（可选；不传时由后端按当前租户主体档案推导）", example = "1")
    private Long entId;
    @Schema(description = "来源类型：QUAL 资质主档 / SAFETY_PERMIT 安许证 / STAFF 人员配置（空=全部）", example = "QUAL")
    private String refType;

    @Schema(description = "预警级别 code（字典 risk_level）", example = "WARNING")
    private String alertLevel;

    @Schema(description = "是否已处理（空=全部）", example = "false")
    private Boolean handled;
}
