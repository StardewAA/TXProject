package cn.iocoder.txgy.module.hrm.service.employee.blacklist;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.blacklist.HrmEmployeeBlacklistPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.blacklist.HrmEmployeeBlacklistSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.blacklist.HrmEmployeeBlacklistDO;

/**
 * HRM 员工黑名单 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeBlacklistService {

    /**
     * 新增黑名单
     *
     * @param createReqVO 黑名单信息
     * @return 编号
     */
    Long createBlacklist(HrmEmployeeBlacklistSaveReqVO createReqVO);

    /**
     * 修改黑名单
     *
     * @param updateReqVO 黑名单信息
     */
    void updateBlacklist(HrmEmployeeBlacklistSaveReqVO updateReqVO);

    /**
     * 解除黑名单（状态置为已解除并记录解除时间）
     *
     * @param id 编号
     */
    void releaseBlacklist(Long id);

    /**
     * 删除黑名单
     *
     * @param id 编号
     */
    void deleteBlacklist(Long id);

    /**
     * 获得黑名单
     *
     * @param id 编号
     * @return 黑名单
     */
    HrmEmployeeBlacklistDO getBlacklist(Long id);

    /**
     * 校验黑名单存在
     *
     * @param id 编号
     * @return 黑名单
     */
    HrmEmployeeBlacklistDO validateBlacklistExists(Long id);

    /**
     * 获得黑名单分页
     *
     * @param pageReqVO 分页条件
     * @return 分页结果
     */
    PageResult<HrmEmployeeBlacklistDO> getBlacklistPage(HrmEmployeeBlacklistPageReqVO pageReqVO);

    /**
     * 校验是否命中黑名单（命中则抛业务异常，用于录用 / 再入职拦截）
     *
     * @param name 姓名
     * @param mobile 手机号
     * @param idCardNo 身份证号（可空）
     */
    void validateNotBlacklisted(String name, String mobile, String idCardNo);

}
