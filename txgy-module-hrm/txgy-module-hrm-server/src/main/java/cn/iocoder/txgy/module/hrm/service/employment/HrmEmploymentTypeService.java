package cn.iocoder.txgy.module.hrm.service.employment;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.employment.vo.HrmEmploymentTypePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employment.vo.HrmEmploymentTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employment.HrmEmploymentTypeDO;

import java.util.List;

/**
 * HRM 用工形式 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmEmploymentTypeService {

    /**
     * 创建用工形式
     *
     * @param reqVO 用工形式信息
     * @return 编号
     */
    Long createEmploymentType(HrmEmploymentTypeSaveReqVO reqVO);

    /**
     * 更新用工形式
     *
     * @param reqVO 用工形式信息
     */
    void updateEmploymentType(HrmEmploymentTypeSaveReqVO reqVO);

    /**
     * 删除用工形式
     *
     * @param id 编号
     */
    void deleteEmploymentType(Long id);

    /**
     * 获得用工形式
     *
     * @param id 编号
     * @return 用工形式
     */
    HrmEmploymentTypeDO getEmploymentType(Long id);

    /**
     * 校验用工形式存在
     *
     * @param id 编号
     * @return 用工形式
     */
    HrmEmploymentTypeDO validateEmploymentTypeExists(Long id);

    /**
     * 获得全部用工形式列表（含停用，供后台维护页使用）
     *
     * @return 列表
     */
    List<HrmEmploymentTypeDO> getEmploymentTypeList();

    /**
     * 获得启用中的用工形式列表（供转员工、员工建档等下拉使用）
     *
     * @return 列表
     */
    List<HrmEmploymentTypeDO> getEnabledEmploymentTypeList();

    /**
     * 获得用工形式分页
     *
     * @param reqVO 分页查询
     * @return 分页
     */
    PageResult<HrmEmploymentTypeDO> getEmploymentTypePage(HrmEmploymentTypePageReqVO reqVO);

    /**
     * 校验"可用于新入职"：必须存在、未停用、且已绑定默认合同模板
     *
     * @param id 用工形式编号
     * @return 用工形式
     */
    HrmEmploymentTypeDO validateUsableForOnboard(Long id);

}
