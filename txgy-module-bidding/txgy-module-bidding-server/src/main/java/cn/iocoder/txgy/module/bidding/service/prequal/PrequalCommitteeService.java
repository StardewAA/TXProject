package cn.iocoder.txgy.module.bidding.service.prequal;

import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalCommitteeSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalCommitteeDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalDO;

import java.util.List;

/**
 * 资格审查委员会服务（条例第18条：国有资金项目强制组建）。
 *
 * <p>设计依据：《电子招标投标全流程说明书》§6.3 第 3 条；《实施条例》第18条
 * （国有资金占控股或主导地位的依法必招项目，招标人应当组建资格审查委员会）、
 * 第37条（组成：招标人代表 + 技术/经济专家，5人以上单数，专家≥2/3）。
 */
public interface PrequalCommitteeService {

    /**
    * 保存资格审查委员会（整单覆盖：先删后插；保存时校验组成合规，条例第37条）。
    *
    * @param reqVO 保存入参
    */
    void save(PrequalCommitteeSaveReqVO reqVO);

    /**
    * 查资格审查委员会成员（按 sort 排序）。
    *
    * @param prequalId 资格预审ID
    * @return 委员会成员列表
    */
    List<PrequalCommitteeDO> listByPrequal(Long prequalId);

    /**
    * 审查前置强制校验（条例第18条）：资格预审所属项目为国有资金占控股或主导的依法必招项目时，
    * 必须已组建资格审查委员会，否则抛 PREQUAL_COMMITTEE_REQUIRED，禁止进入审查。
    *
    * @param prequal 资格预审主记录
    */
    void validateStateOwnedCommittee(PrequalDO prequal);
}
