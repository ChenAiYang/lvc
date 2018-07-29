package top.zhubaiju.lvcc;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * @author iyoung chen create at 2017/4/14 15:13
 */
@SuppressWarnings("unchecked")
public class Cache<T> {
  private String id;
  private String name;
  private String desc;
  private T data;
  private LocalDateTime versionTimestamp;

  protected Cache(){

  }
  protected Cache(String cacheId,String cacheName,String cacheDesc,T data){
    this.id = cacheId;
    this.name = cacheName;
    this.desc = cacheDesc;
    this.data = data;
    this.versionTimestamp = LocalDateTime.now();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDesc() {
    return desc;
  }

  public void setDesc(String desc) {
    this.desc = desc;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  public LocalDateTime getVersionTimestamp() {
    return versionTimestamp;
  }

  public void setVersionTimestamp(LocalDateTime versionTimestamp) {
    this.versionTimestamp = versionTimestamp;
  }
}
