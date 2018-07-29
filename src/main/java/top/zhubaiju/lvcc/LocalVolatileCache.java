package top.zhubaiju.lvcc;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.zhubaiju.common.LVCCConstant;
import top.zhubaiju.common.ZBJException;
import top.zhubaiju.lvcc.support.CreateNodeCallBack;
import top.zhubaiju.lvcc.support.LocalVolatileCacheProcessor;
import top.zhubaiju.lvcc.support.LocalVolatileConfig;

/**
 * @author iyoung chen create at 2017/4/14 15:13
 */

public final class LocalVolatileCache implements Watcher {

  Logger LOG = LoggerFactory.getLogger(LocalVolatileCache.class);

  private ConcurrentHashMap<String, Cache> cache = new ConcurrentHashMap<>();

  private LocalVolatileCacheProcessor cacheProcessor ;

  private LocalVolatileConfig localVolatileConfig;

  private ZooKeeper zk;

  public LocalVolatileConfig getLocalVolatileConfig() {
    return localVolatileConfig;
  }

  public void setLocalVolatileConfig(LocalVolatileConfig localVolatileConfig) {
    this.localVolatileConfig = localVolatileConfig;
  }

  public LocalVolatileCacheProcessor getCacheProcessor() {
    return cacheProcessor;
  }

  public void setCacheProcessor(LocalVolatileCacheProcessor cacheProcessor) {
    this.cacheProcessor = cacheProcessor;
  }

  public void init() {
    if( Objects.isNull(cacheProcessor) ){
      throw new ZBJException("【Illegal LocalVolatileCache】 -cacheProcessor can not be null !");
    }
    if (Objects.isNull(localVolatileConfig)) {
      throw new ZBJException("【Illegal LocalVolatileCache】 -localVolatileConfig can not be null !");
    }
    initZKConnection(localVolatileConfig.getZkServerURL(),localVolatileConfig.getSessionTimeOut().intValue());
  }

  /**
   * when clusterSwitch true,then  init ZK
   */
  private void initZKConnection(String zkServerUrl,Integer sessionTimeOut) {
    if (localVolatileConfig.getClusterSwitch().booleanValue()) {
      try {
        //when connect zk, add watcher to notify child node add
        zk = new ZooKeeper(zkServerUrl,
            sessionTimeOut, this);
        List<ACL> aclList = Ids.OPEN_ACL_UNSAFE;
        if (localVolatileConfig.needAuthSec()) {
          aclList = localVolatileConfig.generateACL();
        }
        initZKCacheNodePath(zk, aclList);

      } catch (IOException e) {
        LOG.error("【LocalVolatileCache】 [initZKConnection] Happend IOExcepiton  :", e);
      }
    }
  }

  /**
   * Init zk cache-node path,after init success, set listener for children node <br/>
   * idempotent method
   */
  private void initZKCacheNodePath(ZooKeeper zk, List<ACL> aclList) {
    String currentCacheNodePath = localVolatileConfig.zkCacheNodePath();
    String currentCacheNodeName = localVolatileConfig.getNamespace() + "-" + localVolatileConfig.getModule();
    try {
      Stat stat = zk
          .exists( currentCacheNodePath,
              this);
      if (Objects.nonNull(stat)) {
        LOG.info(
            "【LocalVolatileCache.initZKCacheNodePath】 :【{}】 Already Exist.",currentCacheNodePath);
      } else {
        zk.create(currentCacheNodePath,currentCacheNodeName.getBytes(LVCCConstant.CHAR_SET), aclList,
            CreateMode.PERSISTENT,
            new CreateNodeCallBack(), "Create Node Success");
        //create node with sync mode.wait for 2 second.
        LockSupport.parkUntil(System.currentTimeMillis()+1000*2);
        LOG.info("【initZKConnection】- zkCacheNode :【{}】 Create Success.",currentCacheNodePath);
      }
      // set children listen after init cachaeNode
      zk.getChildren(currentCacheNodePath, this);
    } catch (KeeperException e) {
      LOG.error("【LocalVolatileCache】 [initZKCacheNodePath] Happend KeeperException  :", e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache】 [initZKCacheNodePath] Happend InterruptedException  :", e);
    } catch (UnsupportedEncodingException e) {
      LOG.error("【LocalVolatileCache.initZKCacheNodePath】 happend UnsupportedEncodingException :",
          e);
    }
  }


  /**
   * this method will called when application instant listen zk cache data changed
   * @param cacheId
   */
  private void reload(String cacheId) {
    cacheProcessorCheck("LocalVolatileCache.reload");
    Cache newLocalCache = cacheProcessor.processExpired(cacheId);
    if( Objects.isNull(newLocalCache) ){
      LOG.error("【LocalVolatileCache.reload】- cache id :【{}】 can not load new cache .",cacheId);
      return;
    }
    cache.put(cacheId,newLocalCache);
  }


  /**
   * notify all application instant cache changed
   * @param newLocalCache  newLocalCache
   */
  public void broadcastCacheChange(Cache newLocalCache) {
    CacheBuilder.getInstant().check(newLocalCache);
    String cacheId = newLocalCache.getId();
    if (!localVolatileConfig.getClusterSwitch().booleanValue()) {
      if (cache.contains(cacheId)) {
        cache.put(newLocalCache.getId(), newLocalCache);
      } else {
        LOG.warn("【LocalVolatileCache.broadcastCacheChange】- cacheId:【{}】 do not exists.",
            cacheId);
        return;
      }
    } else {
      modifyRemoteCache(newLocalCache);
    }
    LOG.info("【LocalVolatileCache.broadcastCacheChange】 success.new cache is :{}",
        JSON.toJSONString(newLocalCache));
  }

  /**
   * notify all application instant cache changed
   * @param newLocalCache newLocalCache
   */
  private void modifyRemoteCache(Cache newLocalCache) {
    zkCheck("LocalVolatileCache.modifyRemoteCache");
    try {
      String configNodeName = newLocalCache.getId() + "-" + newLocalCache.getName();
      Stat stat = zk.exists(localVolatileConfig.zkCacheNodePath() + "/" + configNodeName, this);
      if (nonNull(stat)) {
        String newInfo = JSON.toJSONStringWithDateFormat(newLocalCache,"yyyy-MM-dd HH:mm:ss");
        JSONObject jo = JSON.parseObject(newInfo);
        jo.remove("data");
        if (localVolatileConfig.needAuthSec()) {
          //security mode
          zk.addAuthInfo("digest",
              (localVolatileConfig.getAuthP() + ":" + localVolatileConfig.getAuthP())
                  .getBytes(Charset.forName(LVCCConstant.CHAR_SET)));
        }
        zk.setData(localVolatileConfig.zkCacheNodePath() + "/" + configNodeName, (jo.toJSONString()).getBytes(LVCCConstant.CHAR_SET),
            stat.getVersion());
        LOG.info("【LocalVolatileCache.modifyRemoteCache】 - modify remote success.");
        //TODO 修改完毕之后，是否需要再次进行监听
      } else {
        LOG.error(
            "【LocalVolatileCache.modifyRemoteCache】 - cache node :【{}】 do not exists.",localVolatileConfig.zkCacheNodePath() + "/" + configNodeName);
      }
    } catch (KeeperException e) {
      LOG.error("【LocalVolatileCache.modifyRemoteCache】 hanpped KeeperException :", e);
      throw new ZBJException("【LocalVolatileCache.modifyRemoteCache】 happend KeeperException");
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache.modifyRemoteCache】 hanpped InterruptedException :",
          e);
      throw new ZBJException("【LocalVolatileCache.modifyRemoteCache】 happend InterruptedException");
    } catch (UnsupportedEncodingException e) {
      LOG.error(
          "【LocalVolatileCache.modifyRemoteCache】 hanpped UnsupportedEncodingException :",
          e);
      throw new ZBJException("【LocalVolatileCache.modifyRemoteCache】 happend UnsupportedEncodingException");
    }

  }

  /**
   * commit a cache to LVCC manager it .<br/>
   * idempotent method
   *
   * @param localCache - a localCache
   */
  public void commit(Cache localCache) {
    if (Objects.isNull(localCache)) {
      return;
    }
    String cacheId = localCache.getId();

    if (localVolatileConfig.getClusterSwitch().booleanValue()) {
      commitRemote(localCache);
    }
    if (cache.contains(cacheId)) {
      return;
    }
    cache.put(cacheId, localCache);
  }

  /**
   * commit a cache to LVCC-REMOTE manager it<br/>
   * idempotent method
   *
   * @param localCache - localCache
   */
  private void commitRemote(Cache localCache) throws ZBJException {
    zkCheck("LocalVolatileCache.commitRemote");
    try {
      String cacheInstantName = localCache.getId() + "-" + localCache.getName();
      //when register cache meta,add a watcher
      Stat stat = zk.exists(localVolatileConfig.zkCacheNodePath() + "/" + cacheInstantName, this);
      if (nonNull(stat)) {
        LOG.info("【LocalVolatileCache.commitRemote】 - Cache :【{}】 Already exists!",
            JSON.toJSONStringWithDateFormat(localCache,"yyyy-MM-dd HH:mm:ss"));
        return;
      } else {
        List<ACL> aclList = Ids.OPEN_ACL_UNSAFE;
        // not exists
        if (localVolatileConfig.needAuthSec()) {
          aclList = localVolatileConfig.generateACL();
        }

        String temp = JSON.toJSONStringWithDateFormat(localCache,"yyyy-MM-dd HH:mm:ss");
        JSONObject tempObj = JSON.parseObject(temp);
        tempObj.remove("data");
        zk.create(localVolatileConfig.zkCacheNodePath() + "/" + cacheInstantName,
            (tempObj.toJSONString()).getBytes(Charset.forName("utf-8")),
            aclList, CreateMode.PERSISTENT);
        //TODO 新建节点后，是否需要额外设置监听
        LOG.info(
            "【LocalVolatileCache.commitRemote】 - commit remote success. Cache Node Instant info :【{}】 .",
            JSON.toJSONString(tempObj));
      }
    } catch (KeeperException e) {
      LOG.error("【LocalVolatileCache.commitRemote】 hanpped KeeperException :", e);
      throw new ZBJException(
          "【LocalVolatileCache.commitRemote】 commit failure ! - KeeperException ");
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache.commitRemote】 hanpped InterruptedException :", e);
      throw new ZBJException(
          "【LocalVolatileCache.commitRemote】 commit failure ! - InterruptedException ");
    }
  }

  /**
   * remove a cache from LVCC .<br/>
   * idempotent method
   */
  public void remove(Cache localCache) {
    if (Objects.isNull(localCache)) {
      LOG.warn("【LocalVolatileCache.remove】 - try remove a cache which is null  ! ");
      return;
    }
    if (localVolatileConfig.getClusterSwitch().booleanValue()) {
      removeRemote(localCache);
    }
    cache.remove(localCache.getId());
  }

  /**
   * remove a cache from LVCC-REMOTE .<br/>
   * idempotent method
   */
  public void removeRemote(Cache localCache) {
    zkCheck("LocalVolatileCache.removeRemote");
    try {
      String configNodeName = localCache.getId() + "-" + localCache.getName();
      Stat stat = zk.exists(localVolatileConfig.zkCacheNodePath() + "/" + configNodeName, true);
      if (Objects.isNull(stat)) {
        LOG.info(
            "【LocalVolatileCache.removeRemote】Cache【{}】 Do not exists,or delete by other application instant.",
            JSON.toJSONString(localCache));
        return;
      }
      if (localVolatileConfig.needAuthSec()) {
        //security mode
        zk.addAuthInfo("digest",
            (localVolatileConfig.getAuthP() + ":" + localVolatileConfig.getAuthP())
                .getBytes(Charset.forName(LVCCConstant.CHAR_SET)));
      }
      zk.delete(localVolatileConfig.zkCacheNodePath() + "/" + configNodeName, stat.getVersion());
      LOG.info("【LocalVolatileCache.removeRemote】 Cache【{}】delete success.",
          JSON.toJSONString(localCache));
    } catch (KeeperException e) {
      LOG.error("【LocalVolatileCache.removeRemote】execute hanpped KeeperException :", e);
      throw new ZBJException(
          "【LocalVolatileCache.removeRemote】 execute failure ! - KeeperException ");
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache.removeRemote】execute hanpped InterruptedException :", e);
      throw new ZBJException(
          "【LocalVolatileCache.removeRemote】 execute failure ! - KeeperException ");
    }
  }


  /**
   * get local config from cache
   *
   * @param cacheId configID
   * @return Cache return a cache
   */
  public Cache get(String cacheId) {
    Cache localCache = this.cache.get(cacheId);
    if (Objects.isNull(localCache)) {
      cacheProcessorCheck("LocalVolatileCache.get");
      localCache = cacheProcessor.processNotExist(cacheId);
      if (nonNull(localCache)) {
        //TODO 此处是否需要考虑使用异步方式完成添加
        commit(localCache);
      }
    }
    return localCache;
  }

  /**
   * show local cache health info
   *
   * @return return cache health infomation
   */
  public String healthInfo() {
    JSONObject desc = new JSONObject();
    desc.put("clusterSwitch", localVolatileConfig.getClusterSwitch().booleanValue());
    if (localVolatileConfig.getClusterSwitch().booleanValue()) {
      desc.put("zkState", zk.getState());
    }
    desc.put("totalSize(Byte)", JSON.toJSONString(cache.values()).getBytes().length);
    Set<Entry<String, Cache>> entrySet = cache.entrySet();
    for (Entry<String, Cache> el : entrySet) {
      desc.put(el.getKey() + "-" + el.getValue().getName(),
          JSON.toJSONString(el.getValue()).getBytes().length);
    }
    return desc.toJSONString();
  }



  /**
   * remedial measures for connect ZK failure when LocalVolatileCache loading
   *
   * @param zkURL if null,then use original value
   * @param sessionTimeOut sessionTimeOut,then use original value
   * @param retryTimes if null,then default 5
   * @param retryTimesGapMillionSecond if null,then 1000
   */
  public void resetZooKeeperConnection(String zkURL, Integer sessionTimeOut, Integer retryTimes,
      Long retryTimesGapMillionSecond) {
    if (!localVolatileConfig.getClusterSwitch().booleanValue()) {
      LOG.info("【LocalVolatileCache.resetZooKeeperConnection】 execute refuse ! clusterSwitch is false ");
      return;
    }
    if (isNull(zkURL) || Objects.equals("", zkURL)) {
      zkURL = localVolatileConfig.getZkServerURL();
    }
    if (isNull(sessionTimeOut)) {
      sessionTimeOut = localVolatileConfig.getSessionTimeOut().intValue();
    }
    initZKConnection(zkURL, sessionTimeOut);
    Integer leftRetryTimes = retryTimes;
    while (isNull(zk) && leftRetryTimes.intValue() > 0) {
      initZKConnection(zkURL, sessionTimeOut);
    }
  }


  @Override
  public void process(WatchedEvent watchedEvent) {
    KeeperState keeperState = watchedEvent.getState();
    try {
      //relistene children node
      Stat stat = zk.exists(localVolatileConfig.zkCacheNodePath(),this);
      if ( Objects.nonNull(stat) ){
        zk.getChildren( localVolatileConfig.zkCacheNodePath(), this);
      }
    } catch (KeeperException e) {
      LOG.error("【LocalVolatileCache.process】 happend KeeperException :", e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache.process】 happend InterruptedException :", e);
    }
    EventType eventType = watchedEvent.getType();
    String path = watchedEvent.getPath();
    Cache changedCache =null;
    LOG.info("Listen path:【{}】 have changed, change type is {},state is {}", watchedEvent.getPath(),
        watchedEvent.getType(), keeperState);
    if (Objects.nonNull(path) && !Objects.equals("", path) && path
        .startsWith(localVolatileConfig.zkCacheNodePath() + "/")) {
      String newInfo = "";
      try {
        newInfo = new String(this.zk.getData(path, this, null), Charset.forName(LVCCConstant.CHAR_SET));
        changedCache = JSON.parseObject(newInfo, Cache.class);
      } catch (KeeperException e) {
        LOG.error("【LocalVolatileCache.process】 happend KeeperException :", e);
      } catch (InterruptedException e) {
        LOG.error("【LocalVolatileCache.process】 happend InterruptedException :", e);
      }
      LOG.info("new info is :{}", newInfo);
    }

    switch (eventType) {
      case None:
        LOG.info("【LocalVolatileCache.process】 - eventType:None ...");
        if (keeperState == KeeperState.Expired) {
          this.initZKConnection(localVolatileConfig.getZkServerURL(), localVolatileConfig.getSessionTimeOut().intValue());
        }
        break;
      case NodeDataChanged:
        LOG.info("【LocalVolatileCache.process】 - eventType:NodeDataChanged ...");
        if( Objects.nonNull(changedCache) ){
          reload(changedCache.getId());
        }
        break;
      case NodeCreated:
        LOG.info("【LocalVolatileCache.process】 - eventType:NodeCreated ...");
        if( Objects.nonNull(changedCache) ){
          get(changedCache.getId());
        }
        break;
      case NodeDeleted:
        LOG.info("【LocalVolatileCache.process】 - eventType:NodeDeleted ...");
        if( Objects.nonNull(changedCache) ){
          remove(get(changedCache.getId()));
        }
        break;
      case NodeChildrenChanged:
        LOG.info("【LocalVolatileCache.process】 - eventType:NodeChildrenChanged ... do nothing");
        break;
      default:
        LOG.info("【LocalVolatileCache.process】 - eventType:unknow.");
        break;

    }

  }

  private void zkCheck(String methodDesc) {
    if (isNull(zk)) {
      LOG.error(
          "【{}】 execute failure ! ZooKeeper Connection is null !", methodDesc);
      throw new ZBJException(
          String.format("【%s】 execute failure ! Zookeeper Connection is null .", methodDesc)
      );
    }
  }
  private void cacheProcessorCheck(String methodDesc){
    if( isNull(cacheProcessor) ){
      if( Objects.isNull(cacheProcessor) ){
        throw new ZBJException(String.format("【%s】 -cacheProcessor can not be null !",methodDesc));
      }
    }
  }

}
