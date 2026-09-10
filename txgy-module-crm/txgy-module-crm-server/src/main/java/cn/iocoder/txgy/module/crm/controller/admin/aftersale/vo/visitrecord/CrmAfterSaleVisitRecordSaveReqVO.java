package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitrecord;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleSatisfactionEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 售后回访记录 新增/修改 Request VO")
@Data
public class CrmAfterSaleVisitRecordSaveReqVO {

    @Schema(description = "编号，主键自增（更新时必填）", example = "1")
    private Long id;

    @Schema(description = "关联回访任务编号；登记任务回访时必填，手动登记可为空", example = "1")
    private Long taskId;

    @Schema(description = "质保档案编号；关联任务时可空（以任务带出为准）", example = "1")
    @NotNull(message = "质保档案编号不能为空")
    private Long warrantyId;

    @Schema(description = "被访联系人编号，可空", example = "1")
    private Long contactId;

    @Schema(description = "回访方式：1=电话 2=上门 3=微信 4=邮件", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "回访方式不能为空")
    @InEnum(CrmAfterSaleVisitTypeEnum.class)
    private Integer visitType;

    @Schema(description = "回访时间；为空默认当前时间", example = "2026-09-07 10:00:00")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime visitTime;

    @Schema(description = "回访内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "电话回访确认涂层状态良好")
    @NotBlank(message = "回访内容不能为空")
    @Size(max = 2000, message = "回访内容长度不能超过 2000 个字符")
    private String content;

    @Schema(description = "满意度：5=非常满意 4=满意 3=一般 2=不满意 1=非常不满意", example = "5")
    @InEnum(CrmAfterSaleSatisfactionEnum.class)
    private Integer satisfactionLevel;

    @Schema(description = "满意度文字备注", example = "客户对售后响应速度表示认可")
    @Size(max = 500, message = "满意度文字备注长度不能超过 500 个字符")
    private String satisfactionRemark;

    @Schema(description = "建议下次回访日期", example = "2027-03-07")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextVisitDate;

    @Schema(description = "现场照片 URL 数组", example = "[\"https://xxx.com/1.jpg\"]")
    private List<String> picUrls;

    @Schema(description = "附件 URL 数组", example = "[\"https://xxx.com/1.pdf\"]")
    private List<String> fileUrls;

}
