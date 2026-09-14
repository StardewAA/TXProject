package cn.iocoder.txgy.module.product.controller.app.inquiry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
@Data
public class InquirySaveReqVO {
 private Long id;
 @NotBlank @Pattern(regexp="[a-zA-Z0-9-]{16,64}") private String requestKey;
 @NotBlank @Size(max=100) private String title;
 @NotBlank @Size(max=50) private String contactName;
 @NotBlank @Pattern(regexp="1[0-9]{10}") private String contactMobile;
 @NotBlank @Size(max=255) private String address;
 @Size(max=2000) private String remark;
 @NotNull private Long deadline;
 private Long expectedDate;
 @NotEmpty @Size(max=200) @Valid private List<Item> items;
 @Data public static class Item {
  private Long skuId;
  @Size(max=512) private String picUrl;
  @Size(max=100) private String code;
  @NotBlank @Size(max=200) private String name;
  @NotBlank @Size(max=100) private String category;
  @Size(max=500) private String specification;
  @NotNull @DecimalMin("0.001") @Digits(integer=9,fraction=3) private BigDecimal quantity;
  @NotBlank @Size(max=20) private String unit;
  @Size(max=500) private String remark;
 }
}
