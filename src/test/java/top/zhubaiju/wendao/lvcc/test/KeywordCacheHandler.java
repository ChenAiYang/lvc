package top.zhubaiju.wendao.lvcc.test;

import top.zhubaiju.lvcc.Cache;
import top.zhubaiju.lvcc.LocalVolatileCacheProcessor;

public class KeywordCacheHandler implements LocalVolatileCacheProcessor {

  @Override
  public Cache processExpired(String expiredCacheID) {
    Cache newCache = Cache.getInstant(expiredCacheID,"keyword",System.currentTimeMillis()+":version","hello");

    return newCache;
  }

  @Override
  public Cache processNotExist(String notExistCacheID) {
    Cache newCache = Cache.getInstant(notExistCacheID,"keyword","init cache","hello");

    return newCache;
  }
}
