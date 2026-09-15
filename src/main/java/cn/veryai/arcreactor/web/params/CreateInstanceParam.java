package cn.veryai.arcreactor.web.params;

import lombok.Data;

@Data
public class CreateInstanceParam {
  private String name;
  private String description;
  private String flavorId;
  private String imageCategoryId;
  private String regionId;
  private String userId;
  private boolean autoRenew = true;
}
