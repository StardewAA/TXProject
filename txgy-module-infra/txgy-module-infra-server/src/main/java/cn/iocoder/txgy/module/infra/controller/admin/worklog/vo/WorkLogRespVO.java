package cn.iocoder.txgy.module.infra.controller.admin.worklog.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@ExcelIgnoreUnannotated
public class WorkLogRespVO {
    @ExcelProperty("记录编号") private Long id;
    @ExcelProperty("人员编号") private Long userId;
    @ExcelProperty("操作人") private String userName;
    @ExcelProperty("业务模块") private String module;
    @ExcelProperty("操作类型") private String action;
    @ExcelProperty("业务对象") private String businessKey;
    @ExcelProperty("操作内容") private String summary;
    @ExcelProperty("明细分段（按记录编号顺序拼接）") private String contentPart;
    @ExcelProperty("查询条件/请求内容") private String requestParams;
    @ExcelProperty("变更明细") private String changes;
    @ExcelProperty("结果码") private Integer resultCode;
    @ExcelProperty("结果说明") private String resultMsg;
    @ExcelProperty("来源 IP") private String userIp;
    private String userAgent;
    @ExcelProperty("操作时间") private LocalDateTime beginTime;
    @ExcelProperty("耗时（毫秒）") private Integer duration;
    private String traceId;
    private String requestUrl;
    private String requestMethod;
}

