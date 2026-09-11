package cn.iocoder.txgy.module.bidding.controller.admin.project.vo;

// [规范依据]《招投标模块开发规范》§3.2 + 数据规范 V2.0 数据集 BidSectionVO
// [业务目标] 标段登记/修改：必须归属已登记招标项目（校验在服务层），保证"项目→招标项目→标段"追溯链完整
// [为什么] 标段号是公告（BID_SECTION_CODES）与截止时间查询（/notice/exchange/deadline）的定位键，
//           若允许游离标段，追溯会出现"编号存在但上级缺失"断链，故登记时强校验上级存在

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 标段（包） 新建/修改 Request")
@Data
public class BidSectionSaveReqVO {

    @Schema(description = "标段 ID；null=新建，非 null=修改")
    private Long id;

    @Schema(description = "标段（包）编号（新建必填；租户内唯一）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "标段（包）编号不能为空")
    private String bidSectionCode;
    @Schema(description = "所属招标项目编号（必填；须已登记）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "所属招标项目编号不能为空")
    private String tenderProjectCode;
    @Schema(description = "标段（包）名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "标段（包）名称不能为空")
    private String bidSectionName;
    @Schema(description = "统一交易标识码（UNIFIED_DEAL_CODE）")
    private String unifiedDealCode;
    @Schema(description = "标段（包）序号（BID_ECTION_NUM）")
    private String bidEctionNum;
    @Schema(description = "招标方式（BID_TENDER_MODE）")
    private String bidTenderMode;
    @Schema(description = "行政区划（REGION_CODE）")
    private String regionCode;
    @Schema(description = "交易场地代码（TRA_PLACE_CODE）")
    private String traPlaceCode;
    @Schema(description = "标段（包）内容（BID_SECTION_CONTENT）")
    private String bidSectionContent;
    @Schema(description = "标段（包）类型（BID_SECTION_TYPE）")
    private String bidSectionType;
    @Schema(description = "合同估算价（CONTRACT_RECKON_PRICE，字符串保真）")
    private String contractReckonPrice;
    @Schema(description = "币种（CURRENCY_CODE）")
    private String currencyCode;
    @Schema(description = "价格单位（PRICE_UNIT）")
    private String priceUnit;
    @Schema(description = "工期/服务期（TIME_LIMIT）")
    private String timeLimit;
    @Schema(description = "投标人资格条件（BID_QUALIFICATION）")
    private String bidQualification;
    @Schema(description = "是否电子招投标（IS_WEB_BIDDING）")
    private String isWebBidding;
    @Schema(description = "是否不见面开标（IS_NOMEET_BIDDING）")
    private String isNomeetBidding;
    @Schema(description = "内部管理态：ACTIVE/CLOSED")
    private String state;
    @Schema(description = "数据集字段（key=规范英文名；按 BidSectionVO 规整补 null）")
    private Map<String, Object> payload;
    @Schema(description = "备注")
    private String remark;
}
