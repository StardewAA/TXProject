package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;
// [依据] 《招投标模块开发规范》§3.1（VO 规约）；授权/经办用户语义见 00-索引与引用说明.md


import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 已授权主体分页 Request（管理端显示已授权投标人/招标人）")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubjectAuthPageReqVO extends PageParam {

    @Schema(description = "被授权企业租户编号（平台侧可空=全部；企业侧固定自身）")
    private Long tenantId;

    @Schema(description = "主体类型：BIDDER/TENDERER（空=全部）")
    private String subjectType;

    @Schema(description = "授权状态：ENABLED/DISABLED（空=全部）")
    private String authStatus;

    @Schema(description = "企业名称模糊（管理端检索用）")
    private String tenantName;
}
