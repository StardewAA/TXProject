package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 所属企业 响应 VO（RespVO）。
 *
 * <p>规范出处：《招投标模块开发规范》§3.1（自建 VO 命名）、§5 五（T 轨，tenantId 由框架下发）。
 */
@Schema(description = "管理后台 - 所属企业 响应 VO")
@Data
public class SubjectCompanyRespVO {

    @Schema(description = "主键（雪花 ID 十进制字符串下发，前端原样回传）")
    private Long id;

    @Schema(description = "所属租户（T 轨，由框架维护；前端只读展示）")
    private Long tenantId;

    @Schema(description = "企业名称（对应规范字段「单位名称」）")
    private String companyName;

    @Schema(description = "统一社会信用代码（GB32100-2015，18 位；L2）")
    private String uscc;

    @Schema(description = "企业类型 code（subject_company_type：SELF 本企业 / SUBSIDIARY 子公司 / BRANCH 分公司）")
    private String companyType;

    @Schema(description = "上级企业 id（顶级企业为 0；用于本企业→子公司从属）")
    private Long parentId;

    @Schema(description = "上级企业名称快照（列表展示免联表；源变更不回改）")
    private String parentName;

    @Schema(description = "法定代表人姓名（L3）")
    private String legalPersonName;

    @Schema(description = "联系人（L3）")
    private String contactName;

    @Schema(description = "联系电话（L3，列表默认掩码）")
    private String contactPhone;

    @Schema(description = "行政区划代码")
    private String regionCode;

    @Schema(description = "所属行业代码")
    private String industryCode;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否启用（停用只是从选择器隐去，不影响已归属的历史证照/资质）")
    private Boolean enabled;

    @Schema(description = "乐观锁版本")
    private Integer version;

    @Schema(description = "创建时间（epoch 毫秒）")
    private LocalDateTime createTime;

    @Schema(description = "更新时间（epoch 毫秒）")
    private LocalDateTime updateTime;
}
