package cn.iocoder.txgy.module.bidding.controller.admin.project.vo;

// [规范依据]《招投标模块开发规范》§3.2（出参视图 VO）+ §2.2（公开级别裁剪：fields 为 SOCIAL 子集）
// [业务目标] 项目台账/追溯出参：主列 + 数据集公开字段子集

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.LinkedHashMap;

@Schema(description = "管理后台 - 项目 Response")
@Data
public class ProjectRespVO {
    private Long id;
    private String projectCode;
    private String projectName;
    private String regionCode;
    private String investProjectCode;
    private String relateCode;
    private String address;
    private String legalPerson;
    private String industriesType;
    private String fundSource;
    private String contributionScale;
    private String projectScale;
    private String approvalDeptName;
    private String approvalDeptCode;
    private String approvalDocNo;
    private String state;
    private String remark;
    @Schema(description = "数据集公开字段子集（@PublicLevel=SOCIAL；键=规范英文名）")
    private LinkedHashMap<String, Object> fields;
    @Schema(description = "创建时间（epoch 毫秒）")
    private Long createTime;
}
