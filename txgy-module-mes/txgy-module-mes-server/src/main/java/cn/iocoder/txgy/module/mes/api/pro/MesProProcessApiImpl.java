package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProProcessRespDTO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.process.vo.MesProProcessPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.process.MesProProcessDO;
import cn.iocoder.txgy.module.mes.service.pro.process.MesProProcessService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 生产工序 RPC API 实现类
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class MesProProcessApiImpl implements MesProProcessApi {

    @Resource
    private MesProProcessService processService;

    @Override
    public CommonResult<MesProProcessRespDTO> getProcess(Long id) {
        MesProProcessDO process = processService.getProcess(id);
        return success(process == null ? null : BeanUtils.toBean(process, MesProProcessRespDTO.class));
    }

    @Override
    public CommonResult<PageResult<MesProProcessRespDTO>> getProcessPage(String code, String name, Integer status,
                                                                         Integer pageNo, Integer pageSize) {
        MesProProcessPageReqVO pageReqVO = new MesProProcessPageReqVO();
        pageReqVO.setCode(code);
        pageReqVO.setName(name);
        pageReqVO.setStatus(status);
        pageReqVO.setPageNo(pageNo);
        pageReqVO.setPageSize(pageSize);
        PageResult<MesProProcessDO> pageResult = processService.getProcessPage(pageReqVO);
        return success(new PageResult<>(BeanUtils.toBean(pageResult.getList(), MesProProcessRespDTO.class),
                pageResult.getTotal()));
    }

}
