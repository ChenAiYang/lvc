package top.zhubaiju.lvcc;

import java.util.Objects;

/**
 * @author 云中鹤 create at 2017/4/14 15:13
 */
public class LocalVolatileConfig<T> {

  private LocalVolatileConfigMeta configMeta;

  private T configData;

  public LocalVolatileConfigMeta getConfigMeta() {
    return configMeta;
  }

  public void setConfigMeta(LocalVolatileConfigMeta configMeta) {
    this.configMeta = configMeta;
  }

  T getConfigData() {
    return configData;
  }

  void setConfigData(T configData) {
    this.configData = configData;
  }

  private String getConfigNodeName() {
    return configMeta.getConfigNodeName();
  }

  public static <T> LocalVolatileConfig getInstant(String configID,String configName,T data){
    LocalVolatileConfig config = new LocalVolatileConfig();
    config.setConfigData(data);
    LocalVolatileConfigMeta meta = new LocalVolatileConfigMeta();
    meta.setConfigID(configID);
    meta.setConfigName(configName);
    config.setConfigMeta(meta);
    return config;
  }

  public String check(){
    if(Objects.isNull( this.getConfigMeta().getConfigID() )
        || Objects.equals("",this.getConfigMeta().getConfigID())){
      return "configID can not be null";
    }
    if(Objects.isNull( this.getConfigMeta().getConfigName() )
        || Objects.equals("",this.getConfigMeta().getConfigName())){
      return "configName can not be null";
    }
    if( Objects.isNull(this.getConfigData()) ){
      return "config data can not be null";
    }
    return "";
  }

}
