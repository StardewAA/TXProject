package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProRouteRespDTO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.route.vo.MesProRoutePageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.route.MesProRouteDO;
import cn.iocoder.txgy.module.mes.service.pro.route.MesProRouteService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 工艺路线 RPC API 实现类
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class MesProRouteApiImpl implements MesProRouteApi {

    @Resource
    private MesProRouteService routeService;

    @Override
    public CommonResult<MesProRouteRespDTO> getRoute(Long id) {
        MesProRouteDO route = routeService.getRoute(id);
        return success(route == null ? null : BeanUtils.toBean(route, MesProRouteRespDTO.class));
    }

    @Override
    public CommonResult<PageResult<MesProRouteRespDTO>> getRoutePage(String code, String name, Integer status,
                                                                     Integer pageNo, Integer pageSize) {
        MesProRoutePageReqVO pageReqVO = new MesProRoutePageReqVO();
        pageReqVO.setCode(code);
        pageReqVO.setName(name);
        pageReqVO.setStatus(status);
        pageReqVO.setPageNo(pageNo);
        pageReqVO.setPageSize(pageSize);
        PageResult<MesProRouteDO> pageResult = routeService.getRoutePage(pageReqVO);
        return success(new PageResult<>(BeanUtils.toBean(pageResult.getList(), MesProRouteRespDTO.class),
                pageResult.getTotal()));
    }

}
