package cn.iocoder.txgy.module.cms.framework.operatelog;

import cn.iocoder.txgy.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.servlet.ServletUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.system.api.logger.OperateLogApi;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CMS 操作日志切面
 *
 * 拦截标注 {@link CmsOperateLog} 的 Controller 方法，
 * 操作成功后异步写入 system_operate_log（经 system-server RPC）。
 *
 * @author yuting_feng
 */
@Aspect
@Component
@Slf4j
public class CmsOperateLogAspect {

    @Resource
    private OperateLogApi operateLogApi;

    @Around("@annotation(cmsOperateLog)")
    public Object around(ProceedingJoinPoint joinPoint, CmsOperateLog cmsOperateLog) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            recordLog(joinPoint, cmsOperateLog, result);
        } catch (Exception ex) {
            // 日志失败不影响主流程
            log.error("[recordLog][CMS 操作日志记录失败({}/{})]", cmsOperateLog.type(), cmsOperateLog.subType(), ex);
        }
        return result;
    }

    private void recordLog(ProceedingJoinPoint joinPoint, CmsOperateLog cmsOperateLog, Object result) {
        OperateLogCreateReqDTO dto = new OperateLogCreateReqDTO();
        dto.setUserId(SecurityFrameworkUtils.getLoginUserId());
        dto.setUserType(SecurityFrameworkUtils.getLoginUser() != null
                ? SecurityFrameworkUtils.getLoginUser().getUserType() : 2);
        dto.setType(cmsOperateLog.type());
        dto.setSubType(cmsOperateLog.subType());
        dto.setBizId(resolveBizId(joinPoint.getArgs(), result));
        dto.setAction(cmsOperateLog.type() + " " + cmsOperateLog.subType());
        dto.setExtra("");

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            dto.setRequestMethod(request.getMethod());
            dto.setRequestUrl(request.getRequestURI());
            dto.setUserIp(ServletUtils.getClientIP(request));
            dto.setUserAgent(ServletUtils.getUserAgent(request));
        }
        operateLogApi.createOperateLogAsync(dto);
    }

    /**
     * 业务编号：优先取返回的主键（新增）；否则从参数中取（编辑/删除的 id 或 VO 的主键）
     */
    private Long resolveBizId(Object[] args, Object result) {
        if (result instanceof CommonResult) {
            Object data = ((CommonResult<?>) result).getData();
            if (data instanceof Long) {
                return (Long) data;
            }
        }
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            }
            if (arg == null || arg instanceof MultipartFile || arg instanceof Number) {
                continue;
            }
            try {
                java.lang.reflect.Method m = arg.getClass().getMethod("getId");
                Object id = m.invoke(arg);
                if (id instanceof Long) {
                    return (Long) id;
                }
            } catch (Exception ignored) {
                // VO 无 getId 方法时跳过
            }
            try {
                java.lang.reflect.Method[] methods = arg.getClass().getMethods();
                for (java.lang.reflect.Method m : methods) {
                    if (m.getName().endsWith("Id") && m.getParameterCount() == 0
                            && m.getReturnType() == Long.class) {
                        Object id = m.invoke(arg);
                        if (id != null) {
                            return (Long) id;
                        }
                    }
                }
            } catch (Exception ignored) {
                // 忽略反射异常
            }
        }
        return 0L;
    }

}
