package cn.iocoder.txgy.module.trade.service.settlement;
import org.junit.jupiter.api.Test;
import java.time.YearMonth;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
class SettlementMathTest {
 @Test void zeroAndFullCommission(){assertEquals(0,SettlementMath.commission(10000,500,0));assertEquals(9500,SettlementMath.commission(10000,500,10000));}
 @Test void roundingIsPerOrderHalfUp(){assertEquals(1,SettlementMath.commission(1,0,5000));assertEquals(0,SettlementMath.commission(1,0,4999));}
 @Test void refundsReverseOriginalCommission(){assertEquals(-950,SettlementMath.adjustment(10000,0,1000,500));assertEquals(-8550,SettlementMath.adjustment(10000,1000,10000,500));}
 @Test void splitRefundsEqualFullRefund(){long a=SettlementMath.adjustment(101,0,1,3333);long b=SettlementMath.adjustment(101,1,101,3333);assertEquals(SettlementMath.adjustment(101,0,101,3333),a+b);}
 @Test void invalidAmountsRefused(){assertThrows(RuntimeException.class,()->SettlementMath.commission(1,2,0));assertThrows(RuntimeException.class,()->SettlementMath.commission(-1,0,0));assertThrows(RuntimeException.class,()->SettlementMath.commission(10,0,10001));assertThrows(RuntimeException.class,()->SettlementMath.adjustment(10,5,4,0));}
 @Test void onlyClosedCalendarMonths(){assertThrows(RuntimeException.class,()->SettlementMath.month("invalid"));assertThrows(RuntimeException.class,()->SettlementMath.month(YearMonth.now().toString()));assertEquals(YearMonth.of(2020,1),SettlementMath.month("2020-01"));}
 @Test void stateGuard(){assertDoesNotThrow(()->SettlementMath.transition(20,20));assertThrows(RuntimeException.class,()->SettlementMath.transition(10,20));}
 @Test void proofRejectsUrlsAndFakeImages(){assertThrows(RuntimeException.class,()->ShopSettlementService.validateProof("https://example.test/file.png"));assertThrows(RuntimeException.class,()->ShopSettlementService.validateProof("data:image/svg+xml;base64,AA=="));assertThrows(RuntimeException.class,()->ShopSettlementService.validateProof("data:image/png;base64,"+Base64.getEncoder().encodeToString("not an image".getBytes())));}
}
