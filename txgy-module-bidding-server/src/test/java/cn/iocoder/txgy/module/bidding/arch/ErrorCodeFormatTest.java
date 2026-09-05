package cn.iocoder.txgy.module.bidding.arch;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误码规约（《招投标模块开发规范》4.7）：
 *
 * <ol>
 *   <li>只能定义在 server 的 {@code enums/ErrorCodeConstants}（本测试即锚定该类）；</li>
 *   <li>编号格式 {@code 1-060-YYY-ZZZ}（代码字面量 {@code 1_060_YYY_ZZZ}），数值前缀 1/060 固定；</li>
 *   <li>YYY 段必须在文档登记集合内（当前 100~106、199，见规范 4.7 表），新段先改规范 4.7 表再登记到本测试；</li>
 *   <li>全文件编号不得重复。</li>
 * </ol>
 *
 * <p>违规即令 {@code mvn test} 失败。
 */
class ErrorCodeFormatTest {

    private static final String CONSTANTS_CLASS = "cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants";
    private static final String ERROR_CODE_CLASS = "cn.iocoder.txgy.framework.common.exception.ErrorCode";

    /** 与《招投标模块开发规范》4.7 段位表保持一致；新子模块段位先在文档登记，再追加到这里。
     *  106 段 = 外部公告/采集/关注提醒（规范 4.7 表已单列登记）。 */
    private static final int[] ALLOWED_SEGMENTS = {100, 101, 102, 103, 104, 105, 106, 199};

    @Test
    void errorCodesFollowDocumentedSegments() throws Exception {
        Class<?> constants = Class.forName(CONSTANTS_CLASS);
        Class<?> errorCodeClass = Class.forName(ERROR_CODE_CLASS);
        Method getCode = findGetter(errorCodeClass, "getCode");

        List<Integer> seen = new ArrayList<>();
        int count = 0;
        for (Field field : constants.getFields()) {
            if (!errorCodeClass.isAssignableFrom(field.getType())) {
                continue;
            }
            Object value = field.get(null);
            int code = (Integer) getCode.invoke(value);
            count++;

            // 1_060_YYY_ZZZ：亿位=1、百万位段=060
            assertEquals(1, code / 1_000_000_000, field.getName() + " 前缀必须为 1-060-xxx-xxx");
            assertEquals(60, (code / 1_000_000) % 1000, field.getName() + " 前缀必须为 1-060-xxx-xxx");
            int yyy = (code / 1000) % 1000;
            int zzz = code % 1000;
            assertTrue(yyy >= 100, field.getName() + " 段位应为 100+（与 4.7 表一致）");
            assertFalse(seen.contains(code), "错误码重复：" + code + "（" + field.getName() + "）");
            seen.add(code);

            boolean allowed = false;
            for (int segment : ALLOWED_SEGMENTS) {
                if (yyy == segment) {
                    allowed = true;
                    break;
                }
            }
            assertTrue(allowed, field.getName() + " 段位 " + yyy + " 未在 4.7 表登记，先更新规范 4.7 再登记到本测试");
            assertTrue(zzz <= 999, field.getName() + " ZZZ 越界：" + zzz);
        }
        assertTrue(count > 0, "ErrorCodeConstants 未定义任何错误码（或类结构变化）");
    }

    private static Method findGetter(Class<?> type, String name) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }
}
