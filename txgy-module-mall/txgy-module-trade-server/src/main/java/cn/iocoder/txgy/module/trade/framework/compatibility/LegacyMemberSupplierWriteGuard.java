package cn.iocoder.txgy.module.trade.framework.compatibility;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.MEMBER_SUPPLIER_WRITE_DISABLED;

/**
 * 第 21.8 节旧会员销售写入口兼容闸门。
 *
 * <p>默认关闭。只保护旧的 /trade/supplier/** 与 /trade/supplier-settlement/** 写操作；
 * 历史读取、新 admin 企业经营端、企业采购订单及售后均不经过该闸门。</p>
 */
@Component
public class LegacyMemberSupplierWriteGuard {

    @Value("${txgy.trade.compatibility.member-supplier-write-enabled:false}")
    private boolean enabled;

    public void requireEnabled() {
        if (!enabled) {
            throw exception(MEMBER_SUPPLIER_WRITE_DISABLED);
        }
    }
}
