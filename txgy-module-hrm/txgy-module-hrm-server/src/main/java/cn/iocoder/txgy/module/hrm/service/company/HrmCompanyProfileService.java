package cn.iocoder.txgy.module.hrm.service.company;

import cn.iocoder.txgy.module.hrm.controller.admin.company.vo.HrmCompanyProfileSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;

import java.util.List;

/**
 * HRM 公司信息（用工主体）Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmCompanyProfileService {

    /**
     * 保存公司信息：有编号则更新，无编号则新增
     *
     * @param reqVO 公司信息
     * @return 公司编号
     */
    Long saveProfile(HrmCompanyProfileSaveReqVO reqVO);

    /**
     * 删除公司信息
     *
     * @param id 编号
     */
    void deleteProfile(Long id);

    /**
     * 校验公司信息存在
     *
     * @param id 编号
     * @return 公司信息
     */
    HrmCompanyProfileDO validateProfileExists(Long id);

    /**
     * 获得公司信息列表
     *
     * @return 列表
     */
    List<HrmCompanyProfileDO> getProfileList();

    /**
     * 获得默认用工主体（合同渲染使用）
     *
     * @return 公司信息；不存在返回 null
     */
    HrmCompanyProfileDO getDefaultProfile();

}
