package cn.iocoder.txgy.module.bidding.service.prequal;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalDO;

/**
 * 资格预审服务（招标方发起：公告/文件/申请期/状态）。
 *
 * <p>设计依据：《电子招标投标全流程说明书》§6；《实施条例》第15/16/17/19条
 * （资格预审公告、文件发售≥5日、申请期≥5日、结果通知、通过者<3家重新招标）。
 * 入口判断：招标项目主档资格审查方式（qulificationCheckMode）为资格预审时发起本域。
 */
public interface PrequalService {

    /**
     * 创建/修改资格预审（草稿态；新建生成内部编号前缀 P）。
     *
     * @param reqVO 保存入参
     * @return 资格预审ID
     */
    Long save(PrequalSaveReqVO reqVO);

    /**
     * 发布资格预审（DRAFT→PUBLISHED，开始接受申请；发布时校验申请期≥5日，条例第17条）。
     *
     * @param id 资格预审ID
     */
    void publish(Long id);

    /**
     * 关闭资格预审（任意非终态→CLOSED）。
     *
     * @param id 资格预审ID
     */
    void close(Long id);

    /**
     * 查资格预审详情。
     *
     * @param id 资格预审ID
     * @return 资格预审
     */
    PrequalDO get(Long id);

    /**
     * 按编号查资格预审。
     *
     * @param prequalCode 资格预审编号
     * @return 资格预审
     */
    PrequalDO getByCode(String prequalCode);

    /**
     * 资格预审分页。
     *
     * @param reqVO 分页入参
     * @return 分页结果
     */
    PageResult<PrequalDO> page(PrequalPageReqVO reqVO);
}
