package cn.iocoder.txgy.module.bidding.service.subject;
// [依据] 平台授权与经办用户管理语义：管理端“显示已授权投标人 + 支持管理用户”（需求）；授权记录/经办绑定见 DO 注释。
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectAuthPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectAuthRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectAuthSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectUserBindReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectUserPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectUserRespVO;

/**
 * 主体授权与经办用户管理服务（G 轨，平台管理端）。
 */
public interface SubjectAuthService {

    /** 已授权主体分页（管理端；行内含档案完善度，便于识别“已授权未完善”） */
    PageResult<SubjectAuthRespVO> getAuthPage(SubjectAuthPageReqVO reqVO);

    /** 授权（同租户同类型已 ENABLED → 报重复；DISABLED 历史 → 重新启用） */
    Long authorize(SubjectAuthSaveReqVO reqVO);

    /** 授权状态流转（停用=撤销授权保留留痕；重新启用） */
    void updateAuthStatus(Long id, String authStatus);

    /** 经办用户分页（某授权主体下） */
    PageResult<SubjectUserRespVO> getBindUserPage(SubjectUserPageReqVO reqVO);

    /** 绑定经办用户（AdminUser 有效性校验 + 昵称快照；归属租户强校验为系统侧扩展位） */
    Long bindUser(SubjectUserBindReqVO reqVO);

    /** 解绑经办用户（逻辑删除） */
    void unbindUser(Long id);
}
