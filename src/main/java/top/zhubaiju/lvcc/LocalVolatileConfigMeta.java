package top.zhubaiju.lvcc;

/**
 * @author 云中鹤 create at 2017/4/14 15:13
 */
public class LocalVolatileConfigMeta {

  private String configID;
  private String configName;
  private String configDesc="";
  private String configVersion;

  public String getConfigID() {
    return configID;
  }

  public void setConfigID(String configID) {
    this.configID = configID;
  }

  public String getConfigDesc() {
    return configDesc;
  }

  public void setConfigDesc(String configDesc) {
    this.configDesc = configDesc;
  }

  public String getConfigName() {
    return configName;
  }

  public void setConfigName(String configName) {
    this.configName = configName;
  }

  public String getConfigVersion() {
    return configVersion;
  }

  public void setConfigVersion(String configVersion) {
    this.configVersion = configVersion;
  }

  public String getConfigNodeName(){
    return configID+"-"+configName;
  }

}
