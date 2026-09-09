package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.candidate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用于候选人编辑表单的明文数据。该接口仅向具备更新权限的用户开放。
 */
@Schema(description = "管理后台 - HRM 招聘候选人编辑 Response VO")
@Data
public class HrmRecruitCandidateEditRespVO {

    @Schema(description = "候选人编号", example = "1024")
    private Long id;

    @Schema(description = "候选人姓名", example = "张三")
    private String name;

    @Schema(description = "手机号码", example = "15601691399")
    private String mobile;

    @Schema(description = "性别", example = "1")
    private Integer sex;

    @Schema(description = "年龄", example = "28")
    private Integer age;

    @Schema(description = "邮箱", example = "candidate@example.com")
    private String email;

    @Schema(description = "应聘职位编号", example = "1024")
    private Long postId;

    @Schema(description = "工作年限", example = "5")
    private Integer workTime;

    @Schema(description = "学历", example = "5")
    private Integer education;

    @Schema(description = "毕业院校", example = "浙江大学")
    private String graduateSchool;

    @Schema(description = "最近工作单位", example = "某科技公司")
    private String latestWorkPlace;

    @Schema(description = "招聘渠道编号", example = "1")
    private Long channelId;

    @Schema(description = "备注", example = "沟通意向强")
    private String remark;

}
