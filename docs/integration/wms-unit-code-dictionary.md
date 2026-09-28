# 商城—WMS `unitCode` 字典

双方接口统一传输 `unitCode`，中文名称只作为快照展示。商城会把别名归一到编码，但不进行包装和数量换算，例如“箱”不会自动换算成“个”。

完整可导入版本见同目录的 `wms-unit-code-dictionary.csv`。编码共 31 个：

`PC`、`SET`、`GRP`、`BOX`、`CASE`、`BAG`、`BUCKET`、`ROLL`、`SHEET`、`BLOCK`、`BUNDLE`、`BOTTLE`、`CAN`、`TUBE`、`PALLET`、`UNIT`、`M`、`M2`、`M3`、`CM`、`MM`、`KM`、`KG`、`G`、`MG`、`TNE`、`L`、`ML`、`PAIR`、`DOZ`、`KWH`。

当前商城实际使用到的单位均已覆盖：`kg`、`m`、`m²`、`m³`、`㎡`、`个`、`卷`、`块`、`套`、`桶`、`组`、`袋`、`吨`、`台`。WMS 中现存异常单位“呃呃”不纳入字典，相关物料必须先修正主数据后才能出库。

接口约束：

- `unitCode` 必须使用表内大写编码。
- `unitNameSnapshot` 使用对应标准名称。
- 别名只用于录入归一，不作为跨系统传输值。
- 商城订单数量目前为整数；WMS 字段可保留最多两位小数，以兼容后续按重量、面积或体积销售。
