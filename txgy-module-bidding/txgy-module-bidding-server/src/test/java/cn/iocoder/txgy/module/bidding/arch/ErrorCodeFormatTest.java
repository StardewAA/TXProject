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
     *  106 段 = 外部公告/采集/关注提醒（规范 4.7 表已单列登记）。
     *  107 段 = 员工资质/人员配证（规范 4.7 表已单列登记，2026-09-13 新增）。
     *  110 段 = 内部编号生成（规范 4.7 表已单列登记，2026-09-13 新增；原误占 108 段 000/001 与规则配置**重复**，
*           本门禁当时报出"错误码重复：1060108000"，故按"先登记规范 4.7 表、再登记到本测试"的流程迁到 110 段）。
     *  109 段 = 证书与资质台账（规范 4.7 表已单列登记，2026-09-13 新增；由 106 段迁出，
     *           原 106 段已登记给外部公告/采集域，两域 000~026 重叠会造成同码两义）。
     *  110 段 = 内部编号生成（备案回执号/内部项目编号等；序列耗尽与获取失败的异常出口）。
     *  111 段 = 招标全流程/状态机（规范 4.7 表已单列登记，2026-09-20 新增；项目/招标项目/标段/公告生命周期
     *           状态流转校验、政府审批预留接口异常出口）。
     *  112 段 = 投标流程跟踪（规范 4.7 表已单列登记，2026-09-18 新增；投标方视角状态机：意向投标→报名→
     *           资质审核→递交加密投标文件，最严格流程补齐）。
     *  113 段 = 智慧标书审查（规范 4.7 表已单列登记，2026-09-18 从 110 段迁入；
     *           原因：远端已把 110 段登记给「内部编号生成」，同码两义，按规范 §4.7 由后落地方让位）。
     *  114 段 = 招标文件下载名单（规范 4.7 表已单列登记，2026-09-28 新增；阶段四获取文件）。
     *  115 段 = 资格预审（规范 4.7 表已单列登记，2026-09-28 新增；阶段五资格预审）。
     *  116 段 = 投标保证金（规范 4.7 表已单列登记，2026-09-28 新增；阶段六投标保证金 2% 限额）。
     *  117 段 = 定标书面报告（规范 4.7 表已单列登记，2026-09-28 新增；招标投标法第47条 15 日提交时限）。
     *  118 段 = 异议（规范 4.7 表已单列登记，2026-09-28 新增；资格预审/招标文件异议，实施条例第22条）。
     *  119 段 = CA 签章台账（规范 4.7 表已单列登记，2026-09-28 新增；文件电子签章记录/验签状态）。
     *  120 段 = 标书智能体：知识库/智能体配置/精准度测试（原登记 110 段，2026-09-23 与「内部编号生成」撞段，
     *           按规范 §4.7 后落地方让位整体迁入 120 段）。
     *  121 段 = 标书智能体运行时契约：项目/事实/检索/docx/草稿/任务影子（原登记 111 段，2026-09-23 与
     *           「招标全流程/状态机」撞段，按规范 §4.7 后落地方让位整体迁入 121 段；自 txgy-module-bid 并入，
     *           迁入后按 060 段重编）。 */
    private static final int[] ALLOWED_SEGMENTS = {100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 199};

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

