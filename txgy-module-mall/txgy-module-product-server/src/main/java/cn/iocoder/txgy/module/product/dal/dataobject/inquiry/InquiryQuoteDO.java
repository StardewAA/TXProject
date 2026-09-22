package cn.iocoder.txgy.module.product.dal.dataobject.inquiry;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("product_purchase_inquiry_quote")
@Data
@EqualsAndHashCode(callSuper = true)
public class InquiryQuoteDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long inquiryId;
    private Long shopId;
    private Long enterpriseId;
    /** 供应企业真实租户；报价行本身仍随询价保存在采购方租户数据域。 */
    private Long supplierTenantId;
    /** 后台供应商首次创建或接管报价的实际操作账号；旧会员报价可为空。 */
    private Long adminUserId;
    private String requestKey;
    /** 0 草稿，1 已提交，2 已采纳，3 未采纳，4 已撤回。 */
    private Integer status;
    /** 报价快照版本，从 1 开始；每次有效突变递增。 */
    private Long version;
    /** 服务端计算的商品行合计，单位：分。 */
    private Long goodsPrice;
    /** 预计运费，单位：分；成交结算仍由交易模块按运费模板重算。 */
    private Integer freightPrice;
    /** 服务端计算的商品行合计加预计运费，单位：分。 */
    private Long totalPrice;
    /** 预计送达日期，毫秒时间戳。 */
    private Long deliveryDate;
    /** 报价有效期，毫秒时间戳。 */
    private Long validUntil;
    private String remark;
    private LocalDateTime submittedAt;
    private LocalDateTime withdrawnAt;
    private LocalDateTime acceptedAt;
}
