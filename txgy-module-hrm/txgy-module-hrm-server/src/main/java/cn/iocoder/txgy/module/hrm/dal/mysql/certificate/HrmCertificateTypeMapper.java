package cn.iocoder.txgy.module.hrm.dal.mysql.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type.HrmCertificateTypePageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface HrmCertificateTypeMapper extends BaseMapperX<HrmCertificateTypeDO> {

    default PageResult<HrmCertificateTypeDO> selectPage(HrmCertificateTypePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmCertificateTypeDO>()
                .likeIfPresent(HrmCertificateTypeDO::getName, reqVO.getName())
                .likeIfPresent(HrmCertificateTypeDO::getCode, reqVO.getCode())
                .eqIfPresent(HrmCertificateTypeDO::getSpecialOperation, reqVO.getSpecialOperation())
                .eqIfPresent(HrmCertificateTypeDO::getNeedReview, reqVO.getNeedReview())
                .eqIfPresent(HrmCertificateTypeDO::getStatus, reqVO.getStatus())
                .orderByAsc(HrmCertificateTypeDO::getSort)
                .orderByDesc(HrmCertificateTypeDO::getId));
    }

    default List<HrmCertificateTypeDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapperX<HrmCertificateTypeDO>()
                .eqIfPresent(HrmCertificateTypeDO::getStatus, status)
                .orderByAsc(HrmCertificateTypeDO::getSort)
                .orderByAsc(HrmCertificateTypeDO::getId));
    }

    default HrmCertificateTypeDO selectByName(String name) {
        return selectOne(new LambdaQueryWrapperX<HrmCertificateTypeDO>()
                .eq(HrmCertificateTypeDO::getName, name)
                .orderByAsc(HrmCertificateTypeDO::getId)
                .last("LIMIT 1"));
    }

    default HrmCertificateTypeDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapperX<HrmCertificateTypeDO>()
                .eq(HrmCertificateTypeDO::getCode, code)
                .orderByAsc(HrmCertificateTypeDO::getId)
                .last("LIMIT 1"));
    }

    default List<HrmCertificateTypeDO> selectListByIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<HrmCertificateTypeDO>()
                .in(HrmCertificateTypeDO::getId, ids));
    }

}
