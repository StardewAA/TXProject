package cn.iocoder.txgy.module.hrm.util;

import cn.hutool.core.util.StrUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 招聘转员工建档时的字段映射工具（问题 10）
 *
 * <p>候选人学历与员工学历是**两套字典、同值不同义**：</p>
 * <ul>
 *   <li>候选人 {@code hrm_recruit_candidate_education}：1 小学、2 初中、3 高中、4 大专、5 本科、6 硕士、7 博士</li>
 *   <li>员工 {@code hrm_employee_education}：1 小学 … 5 技校、6 高中、7 大专、8 本科、9 硕士、10 博士</li>
 * </ul>
 *
 * <p>此前入职建档直接复制数值，导致「本科(5)」被写成「技校(5)」。这里按语义做显式映射。</p>
 *
 * @author OPENLAB BS
 */
public final class HrmOnboardFillUtils {

    /**
     * 候选人学历 → 员工最高学历
     */
    public static Integer mapCandidateEducationToEmployeeEducation(Integer candidateEducation) {
        if (candidateEducation == null) {
            return null;
        }
        return switch (candidateEducation) {
            case 1 -> 1;   // 小学 → 小学
            case 2 -> 2;   // 初中 → 初中
            case 3 -> 6;   // 高中 → 高中
            case 4 -> 7;   // 大专 → 大专
            case 5 -> 8;   // 本科 → 本科
            case 6 -> 9;   // 硕士 → 硕士
            case 7 -> 10;  // 博士 → 博士
            default -> null;
        };
    }

    /**
     * 学历文案 → 员工最高学历（用于从简历里解析出的「本科 / 硕士」等文本）
     */
    public static Integer mapEducationTextToEmployeeEducation(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String value = text.trim();
        if (value.contains("博士后")) {
            return 11;
        }
        if (value.contains("博士")) {
            return 10;
        }
        if (value.contains("硕士") || value.contains("研究生")) {
            return 9;
        }
        if (value.contains("本科") || value.contains("学士")) {
            return 8;
        }
        if (value.contains("大专") || value.contains("专科") || value.contains("高职")) {
            return 7;
        }
        if (value.contains("高中")) {
            return 6;
        }
        if (value.contains("技校") || value.contains("技师")) {
            return 5;
        }
        if (value.contains("中职")) {
            return 4;
        }
        if (value.contains("中专")) {
            return 3;
        }
        if (value.contains("初中")) {
            return 2;
        }
        if (value.contains("小学")) {
            return 1;
        }
        return null;
    }

    /**
     * 简历里「起始/结束时间」的容错解析：支持 2020、2020-06、2020-06-01、2020/06、2020年6月 等写法。
     *
     * @param text        原始文本
     * @param endOfRange  true 表示这是「结束时间」，只有年月时取当月最后一天，避免出现 6 月 1 日就结束的错觉
     * @return 解析结果；无法识别时返回 null
     */
    public static LocalDateTime parseResumeDate(String text, boolean endOfRange) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String value = text.trim()
                .replace("年", "-").replace("月", "-").replace("日", "")
                .replace("/", "-").replace(".", "-")
                .replace("至今", "").replace("现在", "").replace("present", "")
                .replaceAll("\\s+", "");
        value = value.replaceAll("-+$", "");
        if (StrUtil.isBlank(value)) {
            return null;
        }
        List<DateTimeFormatter> dateTimeFormats = List.of(
                DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"),
                DateTimeFormatter.ofPattern("yyyy-M-d H:m"));
        for (DateTimeFormatter formatter : dateTimeFormats) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试其它格式
            }
        }
        String[] parts = value.split("-");
        try {
            int year = Integer.parseInt(parts[0]);
            if (parts.length == 1) {
                LocalDate date = endOfRange ? LocalDate.of(year, 12, 31) : LocalDate.of(year, 1, 1);
                return date.atStartOfDay();
            }
            int month = Integer.parseInt(parts[1]);
            if (month < 1 || month > 12) {
                return null;
            }
            if (parts.length == 2) {
                LocalDate date = endOfRange
                        ? LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
                        : LocalDate.of(year, month, 1);
                return date.atStartOfDay();
            }
            int day = Integer.parseInt(parts[2]);
            return LocalDate.of(year, month, Math.min(day, 28)).atStartOfDay();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private HrmOnboardFillUtils() {
    }

}
