package top.zhubaiju.lvcc.support;

import top.zhubaiju.common.ZBJException;
import top.zhubaiju.lvcc.LocalVolatileCache;

/**
 * extends this class to make sure LocalVolatileCache can response events
 * change
 *
 * @author iyoung chen create at 2017/4/14 15:13
 */
public abstract class LocalVolatileCacheListener {

  /**
   * when LVCC listen cacheKey changed ,this method would be called
   *
   * @param cacheKey cacheKey
   */

  public abstract void onChanged(String cacheKey);

  /**
   * when LVCC listen cacheKey delete ,this method would be called
   *
   * @param lvcc
   * @param cacheKey cacheKey
   */
  public abstract void onDeleted(LocalVolatileCache lvcc,String cacheKey);

  /**
   * when LVCC listen a new cacheKey,thie method would be called
   * @param lvcc
   * @param cacheKey
   */
  public abstract void onAdd(LocalVolatileCache lvcc,String cacheKey);

  /**
   * when application startup ,LVCC client decated a cache that already commited but not in current app instant
   * @param lvcc
   * @param cacheKey
   */
  public abstract void onLose(LocalVolatileCache lvcc, String cacheKey);


  /**
   * when clusterMode is false,you can ignore(do nothing in emplments ) this method.
   * <p>
   * while LVCC-CLIENT hanppend excption eg : lvcc-client(zkClient) net exception ,this method will
   * be called. You shoud do something in this method,like :<br> 1.send exception to application
   * owner by Email or other way you like. <br> 2.call <code>LocalVolatitleCache.reInit()</code>
   * method by your strategy.
   * </p>
   *
   * @param lvcc current LocalVolatileCache instant
   * @throws ZBJException
   */
  public void lvccExceptionNotifycation(LocalVolatileCache lvcc) throws ZBJException {
    lvcc.reInit();
  }

}
