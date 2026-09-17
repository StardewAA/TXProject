package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;
// [依据] 《招投标模块开发规范》§3.1（VO 规约）；字段口径见 国家/陕西 V2.0 数据规范 表89 法人基本信息与 docs/法规依据/00-索引与引用说明.md


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 主体信息档案 Response（含完善度）")
@Data
public class SubjectProfileRespVO {

    @Schema(description = "档案 ID")
    private Long id;
    @Schema(description = "主体类型")
    private String subjectType;
    @Schema(description = "单位名称")
    private String subjectName;
    @Schema(description = "统一社会信用代码")
    private String uscc;
    @Schema(description = "法定代表人姓名")
    private String legalPersonName;
    @Schema(description = "法人机构类别代码")
    private String legalPersonTypeCode;
    @Schema(description = "行政区划代码")
    private String regionCode;
    @Schema(description = "行业代码")
    private String industryCode;
    @Schema(description = "注册资本")
    private BigDecimal regCapital;
    @Schema(description = "注册资本币种数字码")
    private String regCapCurrency;
    @Schema(description = "联系人")
    private String contactName;
    @Schema(description = "联系电话")
    private String contactPhone;
    @Schema(description = "营业执照影像 file_id")
    private Long licenseFileId;
    @Schema(description = "营业执照影像附件名称")
    private String licenseFileName;
    @Schema(description = "权威扩展字段 JSON")
    private String extJson;
    @Schema(description = "是否信息完善（必填清单全齐）")
    private Boolean complete;
    @Schema(description = "缺失必填字段 code 列表（未完善时有值）")
    private List<String> missingRequiredCodes;
    @Schema(description = "缺失必填字段中文名列表（未完善时有值）")
    private List<String> missingRequiredNames;
    @Schema(description = "最近一次完善时间")
    private LocalDateTime completeTime;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
