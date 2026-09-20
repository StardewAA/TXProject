package cn.iocoder.txgy.module.hrm.dal.mysql.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateTypePageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmCertificateTypeMapper extends BaseMapperX<HrmCertificateTypeDO> {

    default PageResult<HrmCertificateTypeDO> selectPage(HrmCertificateTypePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmCertificateTypeDO>()
                .likeIfPresent(HrmCertificateTypeDO::getName, reqVO.getName())
                .eqIfPresent(HrmCertificateTypeDO::getSpecialOperation, reqVO.getSpecialOperation())
                .eqIfPresent(HrmCertificateTypeDO::getMandatory, reqVO.getMandatory())
                .eqIfPresent(HrmCertificateTypeDO::getStatus, reqVO.getStatus())
                .orderByAsc(HrmCertificateTypeDO::getSort)
                .orderByDesc(HrmCertificateTypeDO::getId));
    }

    default List<HrmCertificateTypeDO> selectListAll() {
        return selectList(new LambdaQueryWrapperX<HrmCertificateTypeDO>()
                .orderByAsc(HrmCertificateTypeDO::getSort)
                .orderByDesc(HrmCertificateTypeDO::getId));
    }

    default HrmCertificateTypeDO selectByName(String name) {
        return selectOne(HrmCertificateTypeDO::getName, name);
    }

    default HrmCertificateTypeDO selectByCode(String code) {
        return selectOne(HrmCertificateTypeDO::getCode, code);
    }

}
