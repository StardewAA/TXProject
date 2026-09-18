package cn.iocoder.txgy.module.infra.controller.admin.worklog.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkLogPageReqVO extends PageParam {
    @Pattern(regexp = "SELF|ALL") private String scope = "SELF";
    private Long userId;
    /** Export snapshot boundary; always overwritten by the export endpoint. */
    private Long maxId;
    @Size(max = 200) private String keyword;
    @Size(max = 64) private String module;
    @Pattern(regexp = "QUERY|CREATE|UPDATE|DELETE|APPROVE|REJECT|SUBMIT|EXPORT|IMPORT|OTHER")
    private String action;
    @Pattern(regexp = "SUCCESS|FAILURE") private String resultStatus;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Size(min = 2, max = 2) private LocalDateTime[] beginTime;
}
