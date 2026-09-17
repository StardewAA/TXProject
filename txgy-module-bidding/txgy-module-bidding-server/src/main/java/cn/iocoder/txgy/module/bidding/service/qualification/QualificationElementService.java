package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationAssetDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationPerformanceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStaffConfigDO;

/**
 * 资质四要素数据录入服务（15-3 人员 / 15-4 业绩 / 15-5 资产装备）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十五：要素台账为达标核查（15-6 element_snapshot
 * 时点聚合）的数据底座，录入只做字段级校验与落库；与主系统人员/合同档案后续经 id_card_blind_index、
 * evidence_file_ids 联动去重（本模块先落台账）。
 */
public interface QualificationElementService {

    /** 录入/更新人员配置（15-3；一人一岗盲索引在接主系统人员库后做，先落库）。返回主键。 */
    Long saveStaff(QualificationStaffConfigDO staff);

    /** 录入/更新工程业绩（15-4；qual_id 归属资质，§17.6）。返回主键。 */
    Long savePerformance(QualificationPerformanceDO performance);

    /** 录入/更新资产装备（15-5）。返回主键。 */
    Long saveAsset(QualificationAssetDO asset);
}
