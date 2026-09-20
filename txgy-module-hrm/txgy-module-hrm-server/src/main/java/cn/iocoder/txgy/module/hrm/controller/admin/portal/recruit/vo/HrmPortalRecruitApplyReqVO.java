package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "员工端 - 招聘应聘投递 Request VO")
@Data
public class HrmPortalRecruitApplyReqVO {
    @NotNull(message = "职位编号不能为空")
    private Long postId;
    @NotBlank(message = "姓名不能为空")
    @Size(max = 255, message = "姓名长度不能超过 255 个字符")
    private String name;
    @NotBlank(message = "手机号码不能为空")
    @Size(max = 18, message = "手机号码长度不能超过 18 个字符")
    @Pattern(regexp = "^(\\+?0?\\d{2,4}-?)?\\d{6,11}$", message = "手机号码格式不正确")
    private String mobile;
    @NotNull(message = "性别不能为空")
    private Integer sex;
    private Integer age;
    @Email(message = "邮箱格式不正确")
    private String email;
    private Integer workTime;
    @NotNull(message = "学历不能为空")
    private Integer education;
    private String graduateSchool;
    private String latestWorkPlace;
    /**
     * 简历来源三选一（服务端会校验归属）：
     * 1. resumeVersionId：本人已固化的个人简历版本（含在线结构化内容）；
     * 2. resumeId：本人已保存的个人简历（附件简历，或在线简历草稿）；
     * 3. resumeFileName + resumeFileUrl：兼容旧客户端直接提交附件。
     *
     * <p>因此这里不再强制要求附件文件名与地址，改由服务端按来源分支校验，
     * 否则在线结构化简历无法投递。</p>
     */
    @Size(max = 255, message = "简历文件名长度不能超过 255 个字符")
    private String resumeFileName;
    @Size(max = 1024, message = "简历文件地址长度不能超过 1024 个字符")
    private String resumeFileUrl;
    /** 个人中心已保存的简历编号；服务端会重新校验归属并复制到候选人简历管理。 */
    private Long resumeId;
    /** 本人已固化的个人简历版本编号；与 resumeId 二选一，优先使用版本。 */
    private Long resumeVersionId;
    private String resumeRemark;
    @Size(max = 500, message = "来源说明不能超过 500 个字符")
    private String sourceRemark;
}
