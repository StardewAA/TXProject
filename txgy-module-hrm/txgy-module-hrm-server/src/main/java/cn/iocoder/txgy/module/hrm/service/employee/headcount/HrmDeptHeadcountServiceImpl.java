package cn.iocoder.txgy.module.hrm.service.employee.headcount;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.headcount.HrmDeptHeadcountPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.headcount.HrmDeptHeadcountSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.headcount.HrmDeptHeadcountDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.headcount.HrmDeptHeadcountMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.DEPT_HEADCOUNT_DUPLICATE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.DEPT_HEADCOUNT_NOT_EXISTS;

/**
 * HRM 部门编制 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmDeptHeadcountServiceImpl implements HrmDeptHeadcountService {

    @Resource
    private HrmDeptHeadcountMapper deptHeadcountMapper;
    @Resource
    private HrmEmployeeMapper employeeMapper;

    @Override
    public Long createDeptHeadcount(HrmDeptHeadcountSaveReqVO createReqVO) {
        // 一个部门只允许一条编制记录
        if (deptHeadcountMapper.selectByDeptId(createReqVO.getDeptId()) != null) {
            throw exception(DEPT_HEADCOUNT_DUPLICATE);
        }
        HrmDeptHeadcountDO headcount = BeanUtils.toBean(createReqVO, HrmDeptHeadcountDO.class);
        headcount.setId(null);
        deptHeadcountMapper.insert(headcount);
        return headcount.getId();
    }

    @Override
    public void updateDeptHeadcount(HrmDeptHeadcountSaveReqVO updateReqVO) {
        HrmDeptHeadcountDO exists = validateDeptHeadcountExists(updateReqVO.getId());
        // 换部门时校验目标部门没有编制记录
        if (!exists.getDeptId().equals(updateReqVO.getDeptId())) {
            HrmDeptHeadcountDO target = deptHeadcountMapper.selectByDeptId(updateReqVO.getDeptId());
            if (target != null && !target.getId().equals(updateReqVO.getId())) {
                throw exception(DEPT_HEADCOUNT_DUPLICATE);
            }
        }
        deptHeadcountMapper.updateById(BeanUtils.toBean(updateReqVO, HrmDeptHeadcountDO.class));
    }

    @Override
    public void deleteDeptHeadcount(Long id) {
        validateDeptHeadcountExists(id);
        deptHeadcountMapper.deleteById(id);
    }

    @Override
    public HrmDeptHeadcountDO getDeptHeadcount(Long id) {
        return deptHeadcountMapper.selectById(id);
    }

    @Override
    public HrmDeptHeadcountDO validateDeptHeadcountExists(Long id) {
        HrmDeptHeadcountDO headcount = deptHeadcountMapper.selectById(id);
        if (headcount == null) {
            throw exception(DEPT_HEADCOUNT_NOT_EXISTS);
        }
        return headcount;
    }

    @Override
    public PageResult<HrmDeptHeadcountDO> getDeptHeadcountPage(HrmDeptHeadcountPageReqVO pageReqVO) {
        return deptHeadcountMapper.selectPage(pageReqVO);
    }

    @Override
    public Long getOnboardCount(Long deptId) {
        return employeeMapper.selectActiveCountByDeptId(deptId);
    }

}
