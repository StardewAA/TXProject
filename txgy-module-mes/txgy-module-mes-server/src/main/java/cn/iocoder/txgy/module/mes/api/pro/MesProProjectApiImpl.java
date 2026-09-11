package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProProjectRespDTO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectDO;
import cn.iocoder.txgy.module.mes.service.pro.project.MesProProjectService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 项目 RPC API 实现类
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class MesProProjectApiImpl implements MesProProjectApi {

    @Resource
    private MesProProjectService projectService;

    @Override
    public CommonResult<MesProProjectRespDTO> getProject(Long id) {
        MesProProjectDO project = projectService.getProject(id);
        return success(project == null ? null : BeanUtils.toBean(project, MesProProjectRespDTO.class));
    }

    @Override
    public CommonResult<PageResult<MesProProjectRespDTO>> getProjectPage(String code, String name, Integer status,
                                                                         Integer sourceType, Integer priority,
                                                                         String owner, String dept,
                                                                         Integer pageNo, Integer pageSize) {
        MesProProjectPageReqVO pageReqVO = new MesProProjectPageReqVO();
        pageReqVO.setCode(code);
        pageReqVO.setName(name);
        pageReqVO.setStatus(status);
        pageReqVO.setSourceType(sourceType);
        pageReqVO.setPriority(priority);
        pageReqVO.setOwner(owner);
        pageReqVO.setDept(dept);
        pageReqVO.setPageNo(pageNo);
        pageReqVO.setPageSize(pageSize);
        PageResult<MesProProjectDO> pageResult = projectService.getProjectPage(pageReqVO);
        return success(new PageResult<>(BeanUtils.toBean(pageResult.getList(), MesProProjectRespDTO.class),
                pageResult.getTotal()));
    }

}
