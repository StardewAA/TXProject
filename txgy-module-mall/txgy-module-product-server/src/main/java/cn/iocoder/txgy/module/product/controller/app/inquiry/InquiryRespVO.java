package cn.iocoder.txgy.module.product.controller.app.inquiry;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采购中心询价响应。
 *
 * <p>故意不暴露历史 erpStatus/erpNo：它们只用于旧数据追溯，不是第 21 节新采购 UI 的状态。</p>
 */
@Data
public class InquiryRespVO {
    private Long id;
    private Long memberId;
    private Long enterpriseId;
    private String requestKey;
    private String no;
    private String title;
    private String contactName;
    private String contactMobile;
    private Integer deliveryAreaId;
    private String address;
    private String remark;
    private Long deadline;
    private Long expectedDate;
    private String visibility;
    private Integer status;
    private Long selectedQuoteId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
