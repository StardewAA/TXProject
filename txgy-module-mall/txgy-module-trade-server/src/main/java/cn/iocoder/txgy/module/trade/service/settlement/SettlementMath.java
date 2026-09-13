package cn.iocoder.txgy.module.trade.service.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneId;
import static cn.iocoder.txgy.module.trade.service.supplier.SupplierTradeService.invalid;

public final class SettlementMath {
    private SettlementMath() {}
    public static long commission(long pay, long refund, int rate) {
        if(pay<0 || refund<0 || refund>pay || rate<0 || rate>10000) throw invalid("订单金额或佣金率异常，请先核对原单");
        return BigDecimal.valueOf(pay-refund).multiply(BigDecimal.valueOf(rate))
            .divide(BigDecimal.valueOf(10000),0,RoundingMode.HALF_UP).longValueExact();
    }
    public static long adjustment(long pay, long before, long refund, int rate) {
        if(refund<before) throw invalid("累计退款金额倒退，请联系平台核对");
        return Math.subtractExact(commission(pay,before,rate)-commission(pay,refund,rate),refund-before);
    }
    public static YearMonth month(String value) {
        try {
            YearMonth month=YearMonth.parse(value);
            if(month.getYear()<2000 || !month.isBefore(YearMonth.now(ZoneId.of("Asia/Shanghai")))) throw invalid("只可生成已结束的自然月账单");
            return month;
        } catch(java.time.format.DateTimeParseException e) { throw invalid("月份格式应为YYYY-MM"); }
    }
    public static void transition(int current, int required) {
        if(current!=required)throw invalid("账单状态已变化，请刷新后操作");
    }
}
