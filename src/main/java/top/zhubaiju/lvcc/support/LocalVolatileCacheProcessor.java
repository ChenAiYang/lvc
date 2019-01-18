package top.zhubaiju.lvcc.support;

import top.zhubaiju.lvcc.LocalVolatileCache;

/**
 *
 * implments this interface to make sure LocalVolatileCache can get new config when listen config change
 *
 * @author iyoung chen create at 2017/4/14 15:13
 */
public interface LocalVolatileCacheProcessor {

  /**
   * when LocalVolatileCache listen config expired ,this method would be call and get a new config
   *
   * @param cacheKey cacheKey
   *
   */
  void onChanged(String cacheKey);

  /**
   * when LVCC have no special configID,this method would be call
   * @param cacheKey notExistCacheID
   *
   */
  void onDeleted(String cacheKey);

  void onAdd(String cacheKey);


  /**
   * when clusterMode is false,you can ignore(do nothing in emplments ) this method.
   * <p>
   * while LVCC-CLIENT hanppend excption eg : lvcc-client(zkClient) net exception ,this method
   * will be called. You shoud do something in this method,like :<br>
   *   1.send exception to application owner by Email or other way you like. <br>
   *   2.call <code>LocalVolatitleCache.reInit()</code> method by your strategy.
   * </p>
   * @param lvcc current LocalVolatileCache instant
   */
  void lvccExceptionNotifycation(LocalVolatileCache lvcc);

}
