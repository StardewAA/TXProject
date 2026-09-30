package cn.iocoder.txgy.module.infra.api.worklog;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.infra.api.worklog.dto.WorkLogRespDTO;
import cn.iocoder.txgy.module.infra.controller.admin.worklog.vo.WorkLogPageReqVO;
import cn.iocoder.txgy.module.infra.dal.dataobject.worklog.WorkLogDO;
import cn.iocoder.txgy.module.infra.service.worklog.WorkLogService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 工作留痕 RPC 实现
 *
 * <p><b>安全设计</b>：这里**强制** {@code scope=SELF}，即只查当前登录用户自己的留痕。
 * WorkLogService 会从安全上下文取登录用户与租户，调用方无法通过参数指定查询对象，
 * 因此不存在「借助 AI 或其他模块读取他人操作记录」的可能。
 *
 * @author 天信管业
 */
@RestController
@Validated
public class WorkLogApiImpl implements WorkLogApi {

    @Resource
    private WorkLogService workLogService;

    @Override
    public CommonResult<PageResult<WorkLogRespDTO>> getMyWorkLogPage(String module, String action,
                                                                    String keyword, Integer pageNo,
                                                                    Integer pageSize) {
        WorkLogPageReqVO reqVO = new WorkLogPageReqVO();
        reqVO.setModule(module);
        reqVO.setAction(action);
        reqVO.setKeyword(keyword);
        reqVO.setPageNo(pageNo);
        reqVO.setPageSize(pageSize);
        // 强制只查自己：不暴露任何可指定查询用户的入口
        reqVO.setScope("SELF");
        reqVO.setUserId(null);
        PageResult<WorkLogDO> page = workLogService.page(reqVO);
        return success(BeanUtils.toBean(page, WorkLogRespDTO.class));
    }

}
