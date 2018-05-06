package top.zhubaiju.lvcc;

import java.util.Objects;

/**
 * @author 人山 create at 2017/4/14 15:13
 */
public class Cache<T> {

  private CacheMeta cacheMeta;

  private T data;

  public CacheMeta getCacheMeta() {
    return cacheMeta;
  }

  public void setCacheMeta(CacheMeta cacheMeta) {
    this.cacheMeta = cacheMeta;
  }

  T getData() {
    return data;
  }

  void setData(T data) {
    this.data = data;
  }

  public static <T> Cache getInstant(String cacheId,String cacheName,T data){
    String desc = "";
    return getInstant(cacheId,cacheName,desc,data);
  }
  public static <T> Cache getInstant(String cacheId,String cacheName,String desc,T data){
    Cache cache = new Cache();
    cache.setData(data);
    Cache.CacheMeta meta = new CacheMeta();
    meta.setCacheId(cacheId);
    meta.setCacheName(cacheName);
    meta.setCacheDesc(desc);
    cache.setCacheMeta(meta);
    return cache;
  }

  public String check(){
    if(Objects.isNull( this.getCacheMeta().getCacheId() )
        || Objects.equals("",this.getCacheMeta().getCacheId())){
      return "cacheId can not be null";
    }
    if(Objects.isNull( this.getCacheMeta().getCacheName() )
        || Objects.equals("",this.getCacheMeta().getCacheName())){
      return "cacheName can not be null";
    }
    if( Objects.isNull(this.getData()) ){
      return "config data can not be null";
    }
    return "";
  }

  static class CacheMeta{
    private String cacheId;
    private String cacheName;
    private String cacheDesc="";
    CacheMeta(){}

    public String getCacheId() {
      return cacheId;
    }

    public void setCacheId(String cacheId) {
      this.cacheId = cacheId;
    }

    public String getCacheName() {
      return cacheName;
    }

    public void setCacheName(String cacheName) {
      this.cacheName = cacheName;
    }

    public String getCacheDesc() {
      return cacheDesc;
    }

    public void setCacheDesc(String cacheDesc) {
      this.cacheDesc = cacheDesc;
    }

  }

}
