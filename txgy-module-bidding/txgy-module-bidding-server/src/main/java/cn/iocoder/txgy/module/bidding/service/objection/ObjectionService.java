package cn.iocoder.txgy.module.bidding.service.objection;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionReplyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.objection.ObjectionDO;

/**
 * 投标异议服务（条例第22条：资格预审/招标文件异议提出时限 + 3 日内答复）。
 */
public interface ObjectionService {

    /** 提出异议（校验截止时限：资格预审文件申请截止前 2 日 / 招标文件投标截止前 10 日）。 */
    Long propose(ObjectionSaveReqVO reqVO);

    /** 答复异议（收到后 3 日内，已答复不可重复答复）。 */
    Long reply(ObjectionReplyReqVO reqVO);

    /** 按主键查询。 */
    ObjectionDO get(Long id);

    /** 分页查询。 */
    PageResult<ObjectionDO> page(ObjectionPageReqVO reqVO);
}
