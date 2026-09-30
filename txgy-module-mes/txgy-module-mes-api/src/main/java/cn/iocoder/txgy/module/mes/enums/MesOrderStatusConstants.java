package cn.iocoder.txgy.module.mes.enums;

/**
 * MES 单据状态常量
 *
 * 集中管理各模块单据状态的编号，作为状态值的中央注册中心。
 * 各枚举类引用此处常量，避免硬编码数字。
 *
 * @author OPENLAB BS
 */
public final class MesOrderStatusConstants {

    /**
     * 草稿
     */
    public static final int PREPARE = 0;

    /**
     * 已确认
     */
    public static final int CONFIRMED = 1;
    /**
     * 审批中
     */
    public static final int APPROVING = 2;
    /**
     * 已审批
     */
    public static final int APPROVED = 3;

    /**
     * 已完成
     */
    public static final int FINISHED = 4;
    /**
     * 已取消
     */
    public static final int CANCELLED = 5;

    /**
     * BPM 审批中（单据已提交工作流，等待审批结果回调）
     *
     * 仅接入 BPM 审批的单据使用，避免与本地"审批中/待处理"语义冲突
     */
    public static final int BPM_APPROVING = 10;

}
