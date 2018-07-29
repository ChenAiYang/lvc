package top.zhubaiju.wendao.lvcc.test;

import top.zhubaiju.lvcc.Cache;
import top.zhubaiju.lvcc.support.LocalVolatileCacheProcessor;

public class KeywordCacheHandler implements LocalVolatileCacheProcessor {

  @Override
  public Cache processExpired(String expiredCacheID) {
    Cache newCache = Cache.getInstant(expiredCacheID,"敏感词",System.currentTimeMillis()+":version-中文","中文");

    return newCache;
  }

  @Override
  public Cache processNotExist(String notExistCacheID) {
    Cache newCache = Cache.getInstant(notExistCacheID,"敏感词","好傻的","hello");

    return newCache;
  }
}
