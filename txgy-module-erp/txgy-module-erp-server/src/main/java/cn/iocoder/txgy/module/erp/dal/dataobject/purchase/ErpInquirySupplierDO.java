package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ERP 询价单受邀供应商 DO。
 *
 * <p>记录「这张询价单发给了哪些供应商」，是供应商侧数据隔离的判定依据：
 * 供应商身份的登录账号只能看到自己受邀的询价单，也只能为受邀的询价单报价。</p>
 *
 * <p>没有受邀记录的历史询价单视为「不限受邀范围」，以保持改造前的行为。</p>
 */
@TableName("erp_inquiry_supplier")
@KeySequence("erp_inquiry_supplier_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpInquirySupplierDO extends BaseDO {

    /** 报价状态：未报价 */
    public static final int QUOTE_STATUS_NONE = 0;
    /** 报价状态：已报价 */
    public static final int QUOTE_STATUS_QUOTED = 20;

    @TableId
    private Long id;
    /** 询价单编号 */
    private Long inquiryId;
    /** 供应商编号 */
    private Long supplierId;
    /** 报价状态，见 {@link #QUOTE_STATUS_NONE} / {@link #QUOTE_STATUS_QUOTED} */
    private Integer quoteStatus;
    /** 邀请时间 */
    private LocalDateTime invitedAt;

}
