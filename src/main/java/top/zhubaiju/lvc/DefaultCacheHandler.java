package top.zhubaiju.lvc;

/**
 * @author 云中鹤 create at 2017/4/14
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
