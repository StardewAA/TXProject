package cn.iocoder.txgy.module.bidding.infra.config;

import cn.iocoder.txgy.module.bidding.enums.flow.FlowModeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 招标全流程运行配置（bidding.flow.*）。
 *
 * <ul>
 *   <li>{@code mode}：TEST=测试中（招标方/平台方/政府审批方点击「下一步/审批通过」手动驱动状态流转）；
 *       PROD=实战（时间驱动 + 真实政府审批接口）。</li>
 *   <li>{@code manualAdvanceEnabled}：「下一步」是否可代替时间轴流转（手动执行时间驱动事件）。</li>
 *       <ul>
 *         <li>缺省跟随模式：TEST 默认 true（可手动点下一步），PROD 默认 false（时间轴事件走定时任务）；</li>
 *         <li>显式配置后覆盖模式默认值：PROD 联调期可配 true 临时允许手动推进，待定时任务就绪再关。</li>
 *       </ul>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "bidding.flow")
@Data
public class BiddingFlowProperties {

    /** 运转模式：TEST 测试中 / PROD 实战（默认 TEST）。 */
    private String mode = "TEST";

    /** 是否允许「下一步」代替时间轴流转（时间驱动事件手动执行）。null=跟随模式默认。 */
    private Boolean manualAdvanceEnabled = null;

    public FlowModeEnum getModeEnum() {
        FlowModeEnum modeEnum = FlowModeEnum.of(mode);
        return modeEnum == null ? FlowModeEnum.TEST : modeEnum;
    }

    public boolean isTestMode() {
        return getModeEnum() == FlowModeEnum.TEST;
    }

    /**
     * 是否允许手动执行时间轴事件（投标开始/截止、公示过期等）。
     * 缺省：TEST=true（手动驱动），PROD=false（时间驱动，等定时任务）；显式配置优先。
     */
    public boolean isManualAdvanceEnabled() {
        return Objects.requireNonNullElseGet(manualAdvanceEnabled, this::isTestMode);
    }
}
