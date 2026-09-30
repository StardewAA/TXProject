package cn.iocoder.txgy.module.wms.enums.md;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WmsWarehouseUserRoleEnum {
    MANAGER("MANAGER"), MEMBER("MEMBER");
    private final String code;
}
