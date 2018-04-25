package top.zhubaiju.lvcc;

/**
 *
 * implments this interface to make sure LocalVolatileConfigCache can get new config when listen config change
 *
 * @author 云中鹤 create at 2017/4/14 15:13
 */
public interface LocalVolatileConfigProcessor {

  /**
   * when LocalVolatileConfigCache listen config expired ,this method would be call and get a new config
   *
   * @param expiredConfigID
   * @return a new config
   */
  LocalVolatileConfig processExpired(String expiredConfigID);

  /**
   * when LVCC have no special configID,this method would be call
   * @param notExistConfigID
   * @return
   */
  LocalVolatileConfig processNotExist(String notExistConfigID);

}
