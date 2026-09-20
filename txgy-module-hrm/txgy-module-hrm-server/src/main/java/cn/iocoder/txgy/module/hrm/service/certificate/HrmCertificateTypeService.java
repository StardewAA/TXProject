package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type.HrmCertificateTypePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type.HrmCertificateTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * HRM 证件类型 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmCertificateTypeService {

    /**
     * 创建证件类型
     *
     * @param reqVO 创建信息
     * @return 证件类型编号
     */
    Long createCertificateType(HrmCertificateTypeSaveReqVO reqVO);

    /**
     * 更新证件类型
     *
     * @param reqVO 更新信息
     */
    void updateCertificateType(HrmCertificateTypeSaveReqVO reqVO);

    /**
     * 删除证件类型
     *
     * 已被证件台账使用的类型不允许删除，避免台账丢失类型语义。
     *
     * @param id 证件类型编号
     */
    void deleteCertificateType(Long id);

    /**
     * 获得证件类型
     *
     * @param id 证件类型编号
     * @return 证件类型
     */
    HrmCertificateTypeDO getCertificateType(Long id);

    /**
     * 获得证件类型列表
     *
     * @param status 状态，为空时查询全部
     * @return 证件类型列表
     */
    List<HrmCertificateTypeDO> getCertificateTypeList(Integer status);

    /**
     * 获得证件类型分页
     *
     * @param reqVO 分页查询
     * @return 证件类型分页
     */
    PageResult<HrmCertificateTypeDO> getCertificateTypePage(HrmCertificateTypePageReqVO reqVO);

    /**
     * 校验证件类型存在，不存在时抛出异常
     *
     * @param id 证件类型编号
     * @return 证件类型
     */
    HrmCertificateTypeDO validateCertificateTypeExists(Long id);

    /**
     * 校验证件类型存在且启用，用于台账引用校验
     *
     * @param id 证件类型编号
     * @return 证件类型
     */
    HrmCertificateTypeDO validateCertificateTypeEnabled(Long id);

    /**
     * 获得证件类型 Map，便于批量填充名称
     *
     * @param ids 证件类型编号集合
     * @return 证件类型 Map
     */
    Map<Long, HrmCertificateTypeDO> getCertificateTypeMap(Collection<Long> ids);

}
