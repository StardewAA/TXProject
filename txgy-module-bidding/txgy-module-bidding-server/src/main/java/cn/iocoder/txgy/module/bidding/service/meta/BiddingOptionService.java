package cn.iocoder.txgy.module.bidding.service.meta;

import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.OptionItemRespVO;
import cn.iocoder.txgy.module.bidding.enums.BiddingOptionTypeEnum;

import java.util.List;

/**
 * 统一候选选项 Service（GET /bidding/meta/options）。
 *
 * <p>设计口径：与 {@code MetaDictService}（字典）同属"前置元数据"——所有电子资质页面共用，
 * 因此读取权限按 {@code hasAnyPermissions} 放行（任一域页面权限即可读），
 * 避免按域授权的角色取不到候选值而回落成"手输主键"（那正是本接口要消灭的）。
 */
public interface BiddingOptionService {

    /**
     * 查询候选选项。
     *
     * @param type    候选类型（非空，由 Controller 解析后传入）
     * @param keyword 模糊关键字（可空）：按该类型的名称/编号列匹配
     * @param limit   返回上限（可空，默认 20，最大 50）
     * @return 选项列表（按 id 倒序，最新在前）
     */
    List<OptionItemRespVO> listOptions(BiddingOptionTypeEnum type, String keyword, Integer limit);
}
