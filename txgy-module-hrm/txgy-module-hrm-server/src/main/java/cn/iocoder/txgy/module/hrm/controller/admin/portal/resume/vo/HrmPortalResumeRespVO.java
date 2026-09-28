package cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "员工端 - 个人简历 Response VO")
@Data
public class HrmPortalResumeRespVO {

    private Long id;
    private String title;
    private Integer resumeType;
    private Integer sourceType;
    private String fileName;
    private String fileUrl;
    private String avatarUrl;
    private String content;
    private String remark;
    private LocalDateTime createTime;
}
