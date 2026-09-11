package cn.iocoder.txgy.module.bidding.controller.admin.project.vo;

// [规范依据]《招投标模块开发规范》§3.2 + §2.2 公开级别裁剪
// [业务目标] 标段台账/追溯出参

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.LinkedHashMap;

@Schema(description = "管理后台 - 标段（包） Response")
@Data
public class BidSectionRespVO {
    private Long id;
    private String bidSectionCode;
    private String tenderProjectCode;
    private String bidSectionName;
    private String unifiedDealCode;
    private String bidEctionNum;
    private String bidTenderMode;
    private String regionCode;
    private String traPlaceCode;
    private String bidSectionContent;
    private String bidSectionType;
    private String contractReckonPrice;
    private String currencyCode;
    private String priceUnit;
    private String timeLimit;
    private String bidQualification;
    private String isWebBidding;
    private String isNomeetBidding;
    private String state;
    private String remark;
    @Schema(description = "数据集公开字段子集（@PublicLevel=SOCIAL）")
    private LinkedHashMap<String, Object> fields;
    @Schema(description = "创建时间（epoch 毫秒）")
    private Long createTime;
}
