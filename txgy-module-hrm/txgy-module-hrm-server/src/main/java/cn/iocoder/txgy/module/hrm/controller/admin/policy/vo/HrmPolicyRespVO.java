package cn.iocoder.txgy.module.hrm.controller.admin.policy.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - HRM 政策 Response VO")
@Data
public class HrmPolicyRespVO {

    @Schema(description = "政策编号")
    private Long id;
    @Schema(description = "政策名称")
    private String name;
    @Schema(description = "类别")
    private String category;
    @Schema(description = "文号")
    private String docNo;
    @Schema(description = "发布机关")
    private String publishOrg;
    @Schema(description = "发布日期")
    private LocalDate publishDate;
    @Schema(description = "生效日期")
    private LocalDate effectiveDate;
    @Schema(description = "失效日期")
    private LocalDate expireDate;
    @Schema(description = "适用范围")
    private String scope;
    @Schema(description = "范围明细")
    private String scopeValue;
    @Schema(description = "政策正文")
    private String content;
    @Schema(description = "附件地址")
    private List<String> fileUrls;
    @Schema(description = "标签")
    private List<String> tags;
    @Schema(description = "状态")
    private Integer status;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
