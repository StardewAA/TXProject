package cn.iocoder.txgy.module.pay.enums.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 支付请求的发送生命周期，不是渠道支付结果。旧记录 null 代表未核实，不能推断已发送。 */
@Getter
@AllArgsConstructor
public enum PayOrderSubmitStateEnum {
    PREPARED(0),
    DISPATCHED(1),
    UNKNOWN(2);

    private final Integer state;

    public static boolean isDispatched(Integer state) {
        return DISPATCHED.state.equals(state);
    }
}
