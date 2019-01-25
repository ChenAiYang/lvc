package top.zhubaiju.lvcc;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import org.apache.logging.log4j.util.Strings;
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

/**
 * @author iyoung chen create at 2017/4/14 15:13
 */

public final class LocalVolatileCache implements Watcher {

  Logger LOG = LoggerFactory.getLogger(LocalVolatileCache.class);

  private ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

  private LocalVolatileCacheProcessor cacheProcessor;

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

  public void init() throws ZBJException {
    if (Objects.isNull(cacheProcessor)) {
      throw new ZBJException("【Illegal LocalVolatileCache】 -cacheProcessor can not be null !");
    }
    if (Objects.isNull(localVolatileConfig)) {
      throw new ZBJException("【Illegal LocalVolatileCache】 -localVolatileConfig can not be null !");
    }
    initZKConnection(localVolatileConfig.getZkServerURL(),
        localVolatileConfig.getSessionTimeOut().intValue());
  }

  /**
   * you may call this method when lvccExceptionNotifycation happend.
   *
   * @throws ZBJException re-init  failure
   */
  public void reInit() throws ZBJException {
    if (!localVolatileConfig.getClusterSwitch().booleanValue()) {
      LOG.info(
          "【LocalVolatileCache.reInit】 execute refuse ! clusterSwitch is false ");
      return;
    }
    //if zk re-create success,then listen
    try {
      processDisConnect();
    } catch (KeeperException e) {
      LOG.error("【LocalVolatileCache.reInit】 hanppend KeeperException:", e);
      throw new ZBJException(e.getMessage());
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache.reInit】 hanppend InterruptedException:", e);
      throw new ZBJException(e.getMessage());
    }
    localVolatileConfig.innerClusterSwitch = true;
  }

  /**
   * when clusterSwitch true,then  init ZK
   */
  private void initZKConnection(String zkServerUrl, Integer sessionTimeOut) {
    if (localVolatileConfig.getInnerClusterSwitch().booleanValue()) {
      try {
        //when connect zk, add watcher to notify child node add
        zk = new ZooKeeper(zkServerUrl,
            sessionTimeOut, this);
        List<ACL> aclList = localVolatileConfig.generateACL();
        if (localVolatileConfig.needAuthSec()) {
          zk.addAuthInfo("digest", localVolatileConfig.auth());
        }
        initZKCacheNodePath(zk, aclList);
      } catch (IOException e) {
        LOG.error("【LocalVolatileCache】 [initZKConnection] Happend IOExcepiton  :", e);
      } catch (ZBJException e) {
        LOG.error("【LocalVolatileCache】 [initZKConnection] Happend ZBJException  :", e);
      }
    }
  }

  /**
   * Init zk cache-node path,after init success, set listener for children node <br/> idempotent
   * method
   */
  private void initZKCacheNodePath(ZooKeeper zk, List<ACL> aclList) throws ZBJException {
    String currentCacheNodePath = localVolatileConfig.zkCacheNodePath();
    String currentCacheNodeName =
        localVolatileConfig.getNamespace() + "-" + localVolatileConfig.getModule();
    try {
      String[] nodeList = currentCacheNodePath.split("[/]");
      StringBuilder path = new StringBuilder();
      path.append("/");
      for (String node : nodeList) {
        if (Objects.isNull(node) || Objects.equals("", node)) {
          continue;
        }
        if (!path.toString().endsWith("/")) {
          path.append(LVCCConstant.DEFAULT_BASE_ZK_PATHE);
        }
        path.append(node);
        Stat stat = zk
            .exists(path.toString(),
                this);
        if (Objects.nonNull(stat)) {
          LOG.info(
              "【LocalVolatileCache.initZKCacheNodePath】 :【{}】 Already Exist.", node);
        } else {
          zk.create(path.toString(), node.getBytes(LVCCConstant.CHAR_SET),
              aclList,
              CreateMode.PERSISTENT,
              new CreateNodeCallBack(), "Create Node Success");
          //create node with sync mode.wait for 1 second.
          LockSupport.parkUntil(System.currentTimeMillis() + 1000);
          LOG.info("【initZKConnection】- zkCacheNode :【{}】 Create Success.", currentCacheNodePath);
        }
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
    if (localVolatileConfig.getInnerClusterSwitch() && !localVolatileConfig.getLazyLoad()) {
      loadAllCommitedCache();
    }
  }

  private void loadAllCommitedCache() throws ZBJException {
    List<String> allExistCacheNode = listCacheKey();
    for (String cacheKey : allExistCacheNode) {
      cacheProcessor.onNotExists(this,cacheKey);
    }
  }

  /**
   * get all exists cache node ,at the same time, listen child add/delete
   */
  public List<String> listCacheKey() {
    try {
      zkCheck("LocalVolatileCache.listCacheKey");
      List<String> allCacheNode = zk.getChildren(localVolatileConfig.zkCacheNodePath(), this);
      return allCacheNode;
    } catch (KeeperException e) {
      LOG.error("【LocalVolatileCache.listCacheKey】 execute happend KeeperException", e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache.listCacheKey】 execute happend InterruptedException",
          e);
    } catch (ZBJException e) {
      LOG.error("【LocalVolatileCache.listCacheKey】 execute happend ZBJException", e);
    }
    return new ArrayList<>();
  }



  /**
   * notify all application instant cache changed.at the same time, this method will set watcher for
   * current node <br/>
   *
   * if cacheKey not exists, do nothing 
   *
   * @param cacheKey localCacheId
   * @throws ZBJException bradcast failure
   */
  public void broadcastCacheChange(String cacheKey) throws ZBJException {
    if (Strings.isBlank(cacheKey)) {
      LOG.warn("【LocalVolatileCache.broadcastCacheChange】- cacheId:【{}】 do not exists.",
          cacheKey);
      return;
    }
    // use get() to bind/check-bind cache to lvcc
    if( !cache.containsKey(cacheKey) ){
      return;
    }

    if (!localVolatileConfig.getInnerClusterSwitch().booleanValue()) {
      // if your app is not in cluster mode, LVCC will process change too
      if (cache.contains(cacheKey)) {
        cacheProcessor.onChanged(cacheKey);
        cache.put(cacheKey, "");
      } else {
        LOG.warn(
            "【LocalVolatileCache.broadcastCacheChange】- cacheId:【{}】 do not exists.You may call 【LocalVolatileCache.get()】 first.",
            cacheKey);
        return;
      }
    } else {
      modifyRemoteCache(cacheKey);
    }
    LOG.info("【LocalVolatileCache.broadcastCacheChange】 broadcast cache : 【{}】 success",
        cacheKey);
  }

  /**
   * notify all application instant cache changed
   *
   * @param cacheKey cacheKey
   */
  private void modifyRemoteCache(String cacheKey) throws ZBJException {
    zkCheck("LocalVolatileCache.modifyRemoteCache");
    try {
      String currentCacheNodePath =
          localVolatileConfig.zkCacheNodePath() + "/" + cacheKey;
      Stat stat = zk.exists(currentCacheNodePath, this);
      if (nonNull(stat)) {
        //exists
        JSONObject cacheKeyInfo = new JSONObject();
        cacheKeyInfo.put("cacheKey", cacheKey);
        cacheKeyInfo.put("lastOperateTime", new Date());
        String cacheKeyInfoStr = JSON
            .toJSONStringWithDateFormat(cacheKeyInfo, LVCCConstant.DEFAULT_DATE_FORMATTER);

        zk.setData(currentCacheNodePath,
            cacheKeyInfoStr.getBytes(LVCCConstant.CHAR_SET),
            stat.getVersion());
        LOG.info("【LocalVolatileCache.modifyRemoteCache】 - modify remote success.");
      } else {
        // do not exists
        LOG.error(
            "【LocalVolatileCache.modifyRemoteCache】 - cache node :【{}】 do not exists.",
            currentCacheNodePath);
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
      throw new ZBJException(
          "【LocalVolatileCache.modifyRemoteCache】 happend UnsupportedEncodingException");
    }

  }

  /**
   * commit a cache key to LVCC manager it .<br/> idempotent method
   *
   * @param cacheKey - a local cache key.cacheKey should not be null , empty/trim-empty string
   */
  public void commit(String cacheKey) {
    if (Strings.isBlank(cacheKey)) {
      return;
    }
    if (localVolatileConfig.getInnerClusterSwitch().booleanValue()) {
      commitRemote(cacheKey);
    }
    if (cache.contains(cacheKey)) {
      return;
    }
    cache.put(cacheKey, "");
  }

  /**
   * commit a localCacheKey to LVCC-REMOTE manager it<br/> idempotent method
   *
   * @param cacheKey - localCacheKey
   */
  private void commitRemote(String cacheKey) {
    try {
      zkCheck("LocalVolatileCache.commitRemote");
      String currentCacheNodePath =
          localVolatileConfig.zkCacheNodePath() + "/" + cacheKey;
      /**
       * when register cache key , add a watcher
       */
      Stat stat = zk.exists(currentCacheNodePath, this);
      if (nonNull(stat)) {
        byte[] existsData = zk.getData(currentCacheNodePath, this, stat);
        String cacheInfo = new String(existsData, Charset.forName(LVCCConstant.CHAR_SET));
        LOG.info("【LocalVolatileCache.commitRemote】 - Cache Key :【{}】 Already exists!",
            cacheInfo);
        return;
      } else {
        List<ACL> aclList = Ids.OPEN_ACL_UNSAFE;
        /**
         * not exists
         */
        if (localVolatileConfig.needAuthSec()) {
          aclList = localVolatileConfig.generateACL();
        }
        JSONObject cacheKeyInfo = new JSONObject();
        cacheKeyInfo.put("cacheKey", cacheKey);
        cacheKeyInfo.put("lastOperateTime", new Date());
        String cacheKeyInfoStr = JSON
            .toJSONStringWithDateFormat(cacheKeyInfo, LVCCConstant.DEFAULT_DATE_FORMATTER);
        zk.create(currentCacheNodePath,
            cacheKeyInfoStr.getBytes(Charset.forName(LVCCConstant.CHAR_SET)),
            aclList, CreateMode.PERSISTENT);
        //TODO should add Watcher after create new node ?
        LOG.info(
            "【LocalVolatileCache.commitRemote】 - commit remote success. CacheKey Node Instant info :【{}】 .",
            cacheKeyInfoStr);
      }
    } catch (KeeperException e) {
      LOG.error("【LocalVolatileCache.commitRemote】 hanpped KeeperException :", e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache.commitRemote】 hanpped InterruptedException :", e);
    } catch (ZBJException e) {
      LOG.error("【LocalVolatileCache.commitRemote】 hanpped ZBJException :", e);
    }
  }

  /**
   * remove a cache from LVCC .<br> idempotent method
   *
   * @param cacheKey - a local cache key
   */
  public void remove(String cacheKey) {
    if( Objects.isNull(cacheKey) || Objects.equals("",cacheKey.trim()) ){
      return ;
    }
    if( !cacheKey.contains(cacheKey) ){
      return;
    }
    if (localVolatileConfig.getInnerClusterSwitch().booleanValue()) {
      removeRemote(cacheKey);
    }
    cache.remove(cacheKey);
  }

  /**
   * remove a cache from LVCC-REMOTE .<br> idempotent method
   *
   * @param cacheKey a local cache
   */
  private void removeRemote(String cacheKey) {
    try {
      zkCheck("LocalVolatileCache.removeRemote");
      String currentCacheNodtPath =
          localVolatileConfig.zkCacheNodePath() + "/" + cacheKey;
      Stat stat = zk.exists(currentCacheNodtPath, true);
      if (Objects.isNull(stat)) {
        LOG.info(
            "【LocalVolatileCache.removeRemote】Cache key【{}】 Do not exists,or delete by other application instant.",
            cacheKey);
        return;
      }
      zk.delete(currentCacheNodtPath, stat.getVersion());
      LOG.info("【LocalVolatileCache.removeRemote】 Cache【{}】delete success.",cacheKey);
    } catch (KeeperException e) {
      LOG.error("【LocalVolatileCache.removeRemote】execute hanpped KeeperException :", e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache.removeRemote】execute hanpped InterruptedException :", e);
    } catch (ZBJException e) {
      LOG.error("【LocalVolatileCache.removeRemote】execute hanpped ZBJException :", e);
    }
  }


  @Override
  public void process(WatchedEvent watchedEvent) {
    KeeperState keeperState = watchedEvent.getState();
    //re-listen all children node
    listCacheKey();
    EventType eventType = watchedEvent.getType();
    if (eventType == EventType.None) {
      switch (keeperState) {
        case Expired:
          LOG.error(
              "【LocalVolatileCache.process】 listen session expired :【{}】. ready call processSessionExpired().");
          /**
           * session expired .
           *  - get all exist node(set watcher for all children node)
           *  - broadcast all node is expired
           */
          processSessionExpired();
          break;
        case Disconnected:
          LOG.error("【LocalVolatileCache.process】 listen connection dis...");
          /**
           * Disconnected .
           *  - switch clusterMode false;
           *  - sync try reset zk client with default strategy
           *  - notify application
           */
          localVolatileConfig.innerClusterSwitch = false;
          try {
            cacheProcessor.lvccExceptionNotifycation(this);
          } catch (ZBJException e) {
            e.printStackTrace();
            LOG.error("【LocalVolatileCache.process】execute hanpped ZBJException :", e);
          }
          break;
        default:
          break;
      }

      LOG.info(
          "【LocalVolatileCache.process】 listen event type is None , WatchedEvent.getState is :【{}】,ignore this listen event.",
          keeperState);
      return;
    }
    String path = watchedEvent.getPath();
    String changedCacheId = null;
    if (Objects.nonNull(path) && !Objects.equals("", path) && path
        .startsWith(localVolatileConfig.zkCacheNodePath() + "/")) {
      String[] pathElArray = path.split("/");
      if (pathElArray.length > 1) {
        String cacheNodeName = pathElArray[pathElArray.length - 1];
        changedCacheId = cacheNodeName;
      }
    }

    switch (eventType) {
      case None:
        if (keeperState == KeeperState.Expired) {
          LOG.info("【LocalVolatileCache.process】 - connection is expired . try re-init zk client.");
          this.initZKConnection(localVolatileConfig.getZkServerURL(),
              localVolatileConfig.getSessionTimeOut().intValue());
        }
        break;
      case NodeDataChanged:
        try {
          String newInfo = new String(this.zk.getData(path, this, null),
              Charset.forName(LVCCConstant.CHAR_SET));
          LOG.info(
              "【LocalVolatileCache.process】 - eventType:【NodeDataChanged】.Changed node path is:【{}】,new info is :【{}】 ",
              path, newInfo);
          if (Objects.nonNull(changedCacheId) && !Objects.equals("", changedCacheId)) {
            cacheProcessor.onChanged(changedCacheId);
          }
        } catch (KeeperException e) {
          LOG.error(
              "【LocalVolatileCache.process】-NodeDataChanged,when get new info from zk happend KeeperException :",
              e);
        } catch (InterruptedException e) {
          LOG.error(
              "【LocalVolatileCache.process】-NodeDataChanged,when get new info from zk happend InterruptedException :",
              e);
        }

        break;
      case NodeCreated:
        //TODO  create event really need listen ?????
        /*try {
          String createdInfo = new String(this.zk.getData(path, this, null),
              Charset.forName(LVCCConstant.CHAR_SET));
          LOG.info(
              "【LocalVolatileCache.process】 - eventType:【NodeCreated】.Created node path is:【{}】,created info is :【{}】 ",
              path, createdInfo);
          if (Objects.nonNull(changedCacheId) && !Objects.equals("", changedCacheId)) {
            cacheProcessor.onAdd(changedCacheId);
          }
        } catch (KeeperException e) {
          LOG.error(
              "【LocalVolatileCache.process】-NodeCreated,when get created info from zk happend KeeperException :",
              e);
        } catch (InterruptedException e) {
          LOG.error(
              "【LocalVolatileCache.process】-NodeCreated,when get created info from zk happend InterruptedException :",
              e);
        }*/
        break;
      case NodeDeleted:
        LOG.info(
            "【LocalVolatileCache.process】 - eventType:【NodeDeleted】.Deleted node path is :【{}】",
            path);
        if (Objects.nonNull(changedCacheId) && !Objects.equals("", changedCacheId)) {
          cacheProcessor.onDeleted(changedCacheId);
        }
        break;
      case NodeChildrenChanged:
        if (localVolatileConfig.getInnerClusterSwitch() && localVolatileConfig.getSensitiveAll()) {
          List<String> childNode = listCacheKey();
          LOG.info(
              "【LocalVolatileCache.process】 - eventType:【NodeChildrenChanged】.parent path is :【{}】.Current child node is :【{}】.Ready to manage.",
              path, JSON.toJSON(childNode));
          for (String el : childNode) {
            if( !cache.containsKey(el) ){
              // just listen new node create
              cacheProcessor.onAdd(this,el);
            }
          }
        } else {
          LOG.info(
              "【LocalVolatileCache.process】 - eventType:【NodeChildrenChanged】.new created children node path is :【{}】. Just do nothing",
              path);
        }
        break;
      default:
        LOG.info("【LocalVolatileCache.process】 - eventType:unknow.");
        break;
    }
  }

  /**
   * check zk-client
   */
  private void zkCheck(String methodDesc) throws ZBJException {
    if (isNull(zk)) {
      LOG.error(
          "【{}】 execute failure ! ZooKeeper Connection is null !", methodDesc);
      throw new ZBJException(
          String.format("【%s】 execute failure ! Zookeeper Connection is null .", methodDesc)
      );
    }
  }

  /**
   * check LocalVolatileCacheProcessor can not be null
   */
  private void cacheProcessorCheck(String methodDesc) throws ZBJException {
    if (isNull(cacheProcessor)) {
      if (Objects.isNull(cacheProcessor)) {
        throw new ZBJException(String.format("【%s】 -cacheProcessor can not be null !", methodDesc));
      }
    }
  }

  /**
   * exception : session is expired .<br/> notifycation all cache node expired
   */
  private void processSessionExpired() {
    localVolatileConfig.innerClusterSwitch = true;
    if (localVolatileConfig.getClusterSwitch()) {
      List<String> childCacheNode = listCacheKey();
      for (int i = 0; i < childCacheNode.size(); i++) {
        try {
          broadcastCacheChange(childCacheNode.get(i));
        } catch (ZBJException e) {
          LOG.error("【LocalVolatileCache.processSessionExpired】 ZBJException :", e);
        }
      }
    }
  }

  /**
   * exception : connection id dis-connect<br> notifycation all cache node expired
   */
  private void processDisConnect() throws KeeperException, InterruptedException, ZBJException {
    List<String> allCacheNode = zk.getChildren(localVolatileConfig.zkCacheNodePath(), this);
    if (localVolatileConfig.getClusterSwitch()) {
      for (int i = 0; i < allCacheNode.size(); i++) {
        broadcastCacheChange(allCacheNode.get(i));
      }
    }
  }

}
