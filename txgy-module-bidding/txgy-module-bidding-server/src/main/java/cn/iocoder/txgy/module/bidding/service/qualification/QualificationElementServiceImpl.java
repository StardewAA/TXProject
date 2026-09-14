package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationAssetDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationPerformanceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStaffConfigDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationAssetMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationPerformanceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationStaffConfigMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 资质要素录入服务实现（薄壳委托 Mapper，字段默认值补齐）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3/15-4/15-5；《招投标模块开发规范》§4.5
 * （要素台账无独立状态机，乐观锁版本由前端带版本更新，本阶段只录新）。
 * <p>设计遵循/针对：① is_full_time 在职默认 TRUE（挂靠核查默认口径）；② performance.is_platform_verified
 * 默认 FALSE（线上核验由后续同步补写）；③ 敏感身份证仅可由已脱敏上游传入，本服务不接收明文（L4 边界）。
 */
@Slf4j
@Service
public class QualificationElementServiceImpl implements QualificationElementService {

    @Resource
    private QualificationStaffConfigMapper qualificationStaffConfigMapper;
    @Resource
    private QualificationPerformanceMapper qualificationPerformanceMapper;
    @Resource
    private QualificationAssetMapper qualificationAssetMapper;

    @Override
    public Long saveStaff(QualificationStaffConfigDO staff) {
        if (staff.getIsFullTime() == null) {
            staff.setIsFullTime(Boolean.TRUE);
        }
        qualificationStaffConfigMapper.insert(staff);
        log.info("[qual-element][staff] ent={} qual={} role={} name={} id={}", staff.getEntId(),
                staff.getQualId(), staff.getStaffRole(), staff.getPersonName(), staff.getId());
        return staff.getId();
    }

    @Override
    public Long savePerformance(QualificationPerformanceDO performance) {
        if (performance.getIsPlatformVerified() == null) {
            performance.setIsPlatformVerified(Boolean.FALSE);
        }
        qualificationPerformanceMapper.insert(performance);
        return performance.getId();
    }

    @Override
    public Long saveAsset(QualificationAssetDO asset) {
        qualificationAssetMapper.insert(asset);
        return asset.getId();
    }
}
