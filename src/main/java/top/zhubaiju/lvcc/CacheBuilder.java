package top.zhubaiju.lvcc;

import java.util.Objects;
import top.zhubaiju.common.ZBJException;

/**
 * @author iyoung chen create at 2017/4/14 15:13
 */

public class CacheBuilder {

  public <T> Cache build(String cacheId, String cacheName, String cacheDesc, T data) {
    Cache cache = new Cache<>(cacheId, cacheName, cacheDesc, data);
    check(cache);
    return cache;
  }

  public  <T> Cache build(String cacheId, String cacheName, T data) {
    return build(cacheId, cacheName, "", data);

  }

  public void check(Cache cache) throws ZBJException {
    if( Objects.isNull(cache) ){
      throw new ZBJException("【Illegal Cache】: cache is null.");
    }
    String cacheId = cache.getId();
    String cacheName = cache.getName();
    if (Objects.isNull(cacheId)
        || Objects.equals("", cacheId)) {
      throw new ZBJException("【Illegal Cache】: cacheId is empty or null !");
    }
    if (Objects.isNull(cacheName)
        || Objects.equals("", cacheName)) {
      throw new ZBJException("【Illegal Cache】: cacheName is empty or null !");
    }
    if (Objects.isNull(cache.getData())) {
      throw new ZBJException("【Illegal Cache】: cacheData is null !");
    }
  }

  private CacheBuilder(){}

  public static CacheBuilder getInstant(){
    return new CacheBuilder();
  }

}
