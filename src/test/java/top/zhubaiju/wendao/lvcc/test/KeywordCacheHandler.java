package top.zhubaiju.wendao.lvcc.test;

import top.zhubaiju.lvc.Cache;
import top.zhubaiju.lvc.LocalVolatileCacheProcessor;

public class KeywordCacheHandler implements LocalVolatileCacheProcessor {

  @Override
  public Cache processExpired(String expiredCacheID) {
    return Cache.getInstant(expiredCacheID,"keyword","hello -version ");
  }

  @Override
  public Cache processNotExist(String notExistCacheID) {
    return Cache.getInstant(notExistCacheID,"keyword","hello");
  }
}
