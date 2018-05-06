package top.zhubaiju.lvcc;

/**
 *
 * implments this interface to make sure LocalVolatileCache can get new config when listen config change
 *
 * @author 人山 create at 2017/4/14 15:13
 */
public interface LocalVolatileCacheProcessor {

  /**
   * when LocalVolatileCache listen config expired ,this method would be call and get a new config
   *
   * @param expiredCacheID
   * @return a new config
   */
  Cache processExpired(String expiredCacheID);

  /**
   * when LVCC have no special configID,this method would be call
   * @param notExistCacheID
   * @return
   */
  Cache processNotExist(String notExistCacheID);

}
