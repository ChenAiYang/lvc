package top.zhubaiju.lvcc.support;

import top.zhubaiju.lvcc.Cache;
import top.zhubaiju.lvcc.support.LocalVolatileCacheProcessor;

/**
 * @author iyoung chen create at 2017/4/14
 */
public class DefaultCacheHandler implements LocalVolatileCacheProcessor {


  @Override
  public Cache processExpired(String expiredCacheID) {
    return null;
  }

  @Override
  public Cache processNotExist(String notExistCacheID) {
    return null;
  }
}
