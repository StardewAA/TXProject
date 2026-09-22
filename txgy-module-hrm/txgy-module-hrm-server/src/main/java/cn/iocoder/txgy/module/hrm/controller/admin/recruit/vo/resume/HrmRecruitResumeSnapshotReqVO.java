package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 投递时生成候选人简历快照的请求。
 *
 * <p>与 HR 手动上传简历不同，这里的快照可能只有在线结构化内容而没有附件地址，
 * 因此单独建 VO，避免放宽管理端上传接口的必填校验。</p>
 */
@Schema(description = "管理后台 - HRM 候选人简历快照新增 Request VO")
@Data
public class HrmRecruitResumeSnapshotReqVO {

    @NotNull(message = "候选人编号不能为空")
    private Long candidateId;

    @Size(max = 255, message = "文件名称长度不能超过 255 个字符")
    private String fileName;

    @Size(max = 1024, message = "文件地址长度不能超过 1024 个字符")
    private String fileUrl;

    @Schema(description = "在线结构化简历内容快照")
    @Size(max = 200_000, message = "简历内容过长")
    private String content;

    @Schema(description = "内容指纹")
    @Size(max = 64, message = "内容指纹长度不能超过 64 个字符")
    private String contentHash;

    @Schema(description = "来源个人简历版本编号")
    private Long sourcePersonalVersionId;

    @Schema(description = "投递职位编号")
    private Long postId;

    @Schema(description = "投递时的职位内容指纹")
    @Size(max = 64, message = "职位指纹长度不能超过 64 个字符")
    private String postFingerprint;

    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;
}
