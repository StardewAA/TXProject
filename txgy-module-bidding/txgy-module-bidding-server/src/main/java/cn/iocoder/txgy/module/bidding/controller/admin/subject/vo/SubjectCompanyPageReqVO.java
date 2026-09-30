package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 所属企业分页查询 Request（PageReqVO）。
 *
 * <p>规范出处：《招投标模块开发规范》§3.1（自建 VO 命名）、§4.4（枚举以 code 存储）。
 */
@Schema(description = "管理后台 - 所属企业 分页查询 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubjectCompanyPageReqVO extends PageParam {

    @Schema(description = "企业名称（模糊）")
    private String companyName;

    @Schema(description = "统一社会信用代码（精确）")
    private String uscc;

    @Schema(description = "企业类型 code（subject_company_type：SELF/SUBSIDIARY/BRANCH）")
    private String companyType;

    @Schema(description = "上级企业 id（顶级为 0）")
    private Long parentId;

    @Schema(description = "是否启用（停用不出现在选择器）")
    private Boolean enabled;
}
