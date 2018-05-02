package top.zhubaiju.wendao.lvcc.test;

import top.zhubaiju.lvc.Cache;
import top.zhubaiju.lvc.LocalVolatileCacheProcessor;

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
