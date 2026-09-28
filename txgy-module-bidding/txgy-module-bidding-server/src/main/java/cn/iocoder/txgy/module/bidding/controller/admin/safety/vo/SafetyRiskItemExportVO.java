package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

// [修正] 模块 excel 依赖为 fastexcel（txgy-spring-boot-starter-excel → cn.idev.excel），非 easyexcel；原 import com.alibaba.excel 无法编译
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安许风险条目导出行（五页改造缺接口契约 Q21，2026-09-28；当前筛选范围导出 xlsx）。
 */
@Data
public class SafetyRiskItemExportVO {

    @ExcelProperty("风险等级")
    private String riskLevel;

    @ExcelProperty("风险描述")
    private String riskDesc;

    @ExcelProperty("关联证照")
    private String certRef;

    @ExcelProperty("影响企业")
    private String entName;

    @ExcelProperty("责任人")
    private String assigneeName;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("发现时间")
    private LocalDateTime foundAt;

    @ExcelProperty("处理时间")
    private LocalDateTime handledAt;
}
