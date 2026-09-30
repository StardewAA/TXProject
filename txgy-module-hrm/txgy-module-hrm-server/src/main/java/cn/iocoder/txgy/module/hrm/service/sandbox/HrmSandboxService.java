package cn.iocoder.txgy.module.hrm.service.sandbox;

import cn.iocoder.txgy.module.hrm.dal.dataobject.sandbox.HrmSandboxRecordDO;

import java.util.List;

/**
 * HRM 沙箱（演示/培训）数据 Service 接口（I-025）
 *
 * @author OPENLAB BS
 */
public interface HrmSandboxService {

    /**
     * 列出当前租户已登记的沙箱数据
     */
    List<HrmSandboxRecordDO> getSandboxRecordList();

    /**
     * 生成一套最小演示剧本：1 个草稿招聘职位 +（可选）1 条 HR 代提请假
     *
     * <p>只创建「可安全清理」的数据：职位落草稿态不上门户、请假挂在现成员工上并标记沙箱来源。
     * 不新建部门、不新建后台账号，避免培训数据牵扯系统账号与组织架构。</p>
     *
     * @return 本次登记条数
     */
    int generateDemoScript();

    /**
     * 一键清场：按登记表把沙箱数据逐条软删除，并清空登记
     *
     * @return 实际清理条数
     */
    int clearSandbox();

}
