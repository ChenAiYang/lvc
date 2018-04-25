package top.zhubaiju.lvcc;

/**
 * @author 云中鹤 create at 2017/4/14
 */
public class DefaultConfigHandler implements LocalVolatileConfigProcessor {


  @Override
  public LocalVolatileConfig processExpired(String expiredConfigID) {
    return null;
  }

  @Override
  public LocalVolatileConfig processNotExist(String notExistConfigID) {
    return null;
  }
}
