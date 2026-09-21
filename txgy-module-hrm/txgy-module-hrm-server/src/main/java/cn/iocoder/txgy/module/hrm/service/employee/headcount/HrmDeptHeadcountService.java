package cn.iocoder.txgy.module.hrm.service.employee.headcount;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.headcount.HrmDeptHeadcountPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.headcount.HrmDeptHeadcountSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.headcount.HrmDeptHeadcountDO;

/**
 * HRM 部门编制 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmDeptHeadcountService {

    /**
     * 新增部门编制
     *
     * @param createReqVO 编制信息
     * @return 编号
     */
    Long createDeptHeadcount(HrmDeptHeadcountSaveReqVO createReqVO);

    /**
     * 修改部门编制
     *
     * @param updateReqVO 编制信息
     */
    void updateDeptHeadcount(HrmDeptHeadcountSaveReqVO updateReqVO);

    /**
     * 删除部门编制
     *
     * @param id 编号
     */
    void deleteDeptHeadcount(Long id);

    /**
     * 获得部门编制
     *
     * @param id 编号
     * @return 编制
     */
    HrmDeptHeadcountDO getDeptHeadcount(Long id);

    /**
     * 校验编制存在
     *
     * @param id 编号
     * @return 编制
     */
    HrmDeptHeadcountDO validateDeptHeadcountExists(Long id);

    /**
     * 获得部门编制分页
     *
     * @param pageReqVO 分页条件
     * @return 分页结果
     */
    PageResult<HrmDeptHeadcountDO> getDeptHeadcountPage(HrmDeptHeadcountPageReqVO pageReqVO);

    /**
     * 统计某部门在册人数（在职类）
     *
     * @param deptId 部门编号
     * @return 在册人数
     */
    Long getOnboardCount(Long deptId);

}
