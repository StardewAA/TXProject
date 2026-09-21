package cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto;

import lombok.Data;
import java.util.List;

/** Provider transport facts only; never changes a commerce order. */
@Data
public class ExpressSnapshotRespDTO {
    private String rawStatus;
    private List<ExpressTrackRespDTO> events = List.of();
}
