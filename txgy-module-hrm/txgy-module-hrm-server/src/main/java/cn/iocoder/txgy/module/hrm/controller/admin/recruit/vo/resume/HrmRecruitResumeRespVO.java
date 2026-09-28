package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 招聘简历版本 Response VO")
@Data
public class HrmRecruitResumeRespVO {

    private Long id;
    private Long candidateId;
    private String fileName;
    private Integer versionNo;
    private Integer status;
    private String remark;
    private String fileUrl;
    /** 旧 resume_urls 附件下标；新简历版本为空。 */
    private Integer legacyIndex;
    private LocalDateTime createTime;
}
