package cn.iocoder.txgy.module.hrm.service.recruit.funnel;

import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.funnel.HrmRecruitFunnelRespVO;

import java.util.List;

/**
 * HRM 招聘漏斗 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmRecruitFunnelService {

    /**
     * 默认统计最近月数
     */
    int DEFAULT_MONTHS = 6;

    /**
     * 获得各职位的招聘漏斗（按投递人数倒序）
     *
     * @param months 统计最近几个月，<=0 或空时按 {@link #DEFAULT_MONTHS}
     * @return 漏斗列表
     */
    List<HrmRecruitFunnelRespVO> getFunnelList(Integer months);

}
