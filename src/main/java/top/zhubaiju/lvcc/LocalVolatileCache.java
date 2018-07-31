package top.zhubaiju.lvcc;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
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
import top.zhubaiju.lvcc.support.LocalVolatileConfig;

/**
 * @author iyoung chen create at 2017/4/14 15:13
 */

public final class LocalVolatileCache implements Watcher {

  Logger LOG = LoggerFactory.getLogger(LocalVolatileCache.class);

  private ConcurrentHashMap<String, Cache> cache = new ConcurrentHashMap<>();

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

  public void init() {
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
   * when clusterSwitch true,then  init ZK
   */
  private void initZKConnection(String zkServerUrl, Integer sessionTimeOut) {
    if (localVolatileConfig.getClusterSwitch().booleanValue()) {
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
      }
    }
  }

  /**
   * Init zk cache-node path,after init success, set listener for children node <br/>
   * idempotent method
   */
  private void initZKCacheNodePath(ZooKeeper zk, List<ACL> aclList) {
    String currentCacheNodePath = localVolatileConfig.zkCacheNodePath();
    String currentCacheNodeName =
        localVolatileConfig.getNamespace() + "-" + localVolatileConfig.getModule();
    try {
      Stat stat = zk
          .exists(currentCacheNodePath,
              this);
      if (Objects.nonNull(stat)) {
        LOG.info(
            "【LocalVolatileCache.initZKCacheNodePath】 :【{}】 Already Exist.", currentCacheNodePath);
      } else {
        zk.create(currentCacheNodePath, currentCacheNodeName.getBytes(LVCCConstant.CHAR_SET),
            aclList,
            CreateMode.PERSISTENT,
            new CreateNodeCallBack(), "Create Node Success");
        //create node with sync mode.wait for 2 second.
        LockSupport.parkUntil(System.currentTimeMillis() + 1000 * 2);
        LOG.info("【initZKConnection】- zkCacheNode :【{}】 Create Success.", currentCacheNodePath);
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
    if( !localVolatileConfig.getLazyLoad() ){
      loadAllExistCache();
    }
  }

  private void loadAllExistCache(){
    List<String> allExistCacheNode = getAllExistCacheNode();
    for (String cacheNode: allExistCacheNode ) {
      get(cacheNode);
    }
  }

  /**
   * get all exists cache node ,at the same time, listen child add/delete
   * @return
   */
  private List<String> getAllExistCacheNode(){
    zkCheck("LocalVolatileCache.getAllExistCacheNode");
    try {
      List<String> allCacheNode = zk.getChildren(localVolatileConfig.zkCacheNodePath(),this);
      return allCacheNode;
    } catch (KeeperException e) {
      LOG.error("【LocalVolatileCache.getAllExistCacheNode】 execute happend KeeperException",e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache.getAllExistCacheNode】 execute happend InterruptedException",e);
    }
    return new ArrayList<>();
  }


  /**
   * this method will called when application instant listen zk cache data changed
   * @param cacheId cache Id
   * @param reloadDateTime  reload time
   */
  private void reload(String cacheId,LocalDateTime reloadDateTime) {
    cacheProcessorCheck("LocalVolatileCache.reload");
    Cache newLocalCache = cacheProcessor.processExpired(cacheId);
    if (Objects.isNull(newLocalCache)) {
      LOG.error("【LocalVolatileCache.reload】- cache id :【{}】 can not load new cache .", cacheId);
      return;
    }
    newLocalCache.setVersionTimestamp(reloadDateTime);
    cache.put(cacheId, newLocalCache);
  }


  /**
   * notify all application instant cache changed
   *
   * @param localCacheId localCacheId
   */
  public void broadcastCacheChange(String localCacheId) {
    if (Strings.isBlank(localCacheId)) {
      LOG.warn("【LocalVolatileCache.broadcastCacheChange】- cacheId:【{}】 do not exists.",
          localCacheId);
      return;
    }
    Cache newLocalCache = cacheProcessor.processExpired(localCacheId);
    if (Objects.isNull(newLocalCache)) {
      LOG.warn(
          "【LocalVolatileCache.broadcastCacheChange】- cacheId:【{}】 can not find a new cache from the implement of LocalVolatileCacheProcessor.",
          localCacheId);
      return;
    }

    if (!localVolatileConfig.getClusterSwitch().booleanValue()) {
      if (cache.contains(localCacheId)) {
        cache.put(newLocalCache.getId(), newLocalCache);
      } else {
        LOG.warn(
            "【LocalVolatileCache.broadcastCacheChange】- cacheId:【{}】 do not exists.You may call 【LocalVolatileCache.commit()】 first.",
            localCacheId);
        return;
      }
    } else {
      modifyRemoteCache(newLocalCache);
    }
    LOG.info("【LocalVolatileCache.broadcastCacheChange】 success.new cache is :{}",
        JSON.toJSONStringWithDateFormat(newLocalCache, LVCCConstant.DEFAULT_DATE_FORMATTER));
  }

  /**
   * notify all application instant cache changed
   *
   * @param newLocalCache newLocalCache
   */
  private void modifyRemoteCache(Cache newLocalCache) {
    zkCheck("LocalVolatileCache.modifyRemoteCache");
    try {
      String currentCacheNodePath =
          localVolatileConfig.zkCacheNodePath() + "/" + newLocalCache.getId();
      Stat stat = zk.exists(currentCacheNodePath, this);
      if (nonNull(stat)) {
        newLocalCache.setVersionTimestamp(LocalDateTime.now());
        String newInfo = JSON
            .toJSONStringWithDateFormat(newLocalCache, LVCCConstant.DEFAULT_DATE_FORMATTER);
        JSONObject jo = JSON.parseObject(newInfo);
        jo.remove("data");
        zk.setData(currentCacheNodePath,
            (jo.toJSONString()).getBytes(LVCCConstant.CHAR_SET),
            stat.getVersion());
        LOG.info("【LocalVolatileCache.modifyRemoteCache】 - modify remote success.");
        //TODO 修改完毕之后，是否需要再次进行监听
      } else {
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
   * commit a cache to LVCC manager it .<br/>
   * idempotent method
   *
   * @param localCache - a localCache
   */
  private void commit(Cache localCache) {
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
      String currentCacheNodePath =
          localVolatileConfig.zkCacheNodePath() + "/" + localCache.getId();

      //when register cache meta,add a watcher
      Stat stat = zk.exists(currentCacheNodePath, this);
      if (nonNull(stat)) {
        byte[] existsData = zk.getData(currentCacheNodePath, this, stat);
        Cache existCache = JSON
            .parseObject(new String(existsData, Charset.forName(LVCCConstant.CHAR_SET)),
                Cache.class);
        localCache.setVersionTimestamp(existCache.getVersionTimestamp());
        LOG.info("【LocalVolatileCache.commitRemote】 - Cache :【{}】 Already exists!",
            JSON.toJSONStringWithDateFormat(localCache, LVCCConstant.DEFAULT_DATE_FORMATTER));
        return;
      } else {
        List<ACL> aclList = Ids.OPEN_ACL_UNSAFE;
        // not exists
        if (localVolatileConfig.needAuthSec()) {
          aclList = localVolatileConfig.generateACL();
        }
        localCache.setVersionTimestamp(LocalDateTime.now());
        String temp = JSON
            .toJSONStringWithDateFormat(localCache, LVCCConstant.DEFAULT_DATE_FORMATTER);
        JSONObject tempObj = JSON.parseObject(temp);
        tempObj.remove("data");
        zk.create(currentCacheNodePath,
            (tempObj.toJSONString()).getBytes(Charset.forName(LVCCConstant.CHAR_SET)),
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
      String currentCacheNodtPath =
          localVolatileConfig.zkCacheNodePath() + "/" + localCache.getId();
      Stat stat = zk.exists(currentCacheNodtPath, true);
      if (Objects.isNull(stat)) {
        LOG.info(
            "【LocalVolatileCache.removeRemote】Cache【{}】 Do not exists,or delete by other application instant.",
            JSON.toJSONStringWithDateFormat(localCache, LVCCConstant.DEFAULT_DATE_FORMATTER));
        return;
      }
      zk.delete(currentCacheNodtPath, stat.getVersion());
      LOG.info("【LocalVolatileCache.removeRemote】 Cache【{}】delete success.",
          JSON.toJSONStringWithDateFormat(localCache, LVCCConstant.DEFAULT_DATE_FORMATTER));
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
    JSONArray cacheList = new JSONArray();
    for (Entry<String, Cache> el : entrySet) {
      JSONObject cacheEl = new JSONObject();
      cacheEl.put("cacheId",el.getKey());
      cacheEl.put("cacheName",el.getValue().getName());
      cacheEl.put("cacheSize(Byte)",JSON.toJSONString(el.getValue()).getBytes(Charset.forName(LVCCConstant.CHAR_SET)).length);
      cacheEl.put("cacheJson",el.getValue());
      cacheList.add(cacheEl);
    }
    desc.put("cacheList",cacheList);
    return JSON.toJSONStringWithDateFormat(desc,LVCCConstant.DEFAULT_DATE_FORMATTER);
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
      LOG.info(
          "【LocalVolatileCache.resetZooKeeperConnection】 execute refuse ! clusterSwitch is false ");
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
    //re-listen all children node
    getAllExistCacheNode();
    EventType eventType = watchedEvent.getType();
    if (eventType == EventType.None) {
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
          Cache temp = JSON.parseObject(newInfo,Cache.class);
          LOG.info(
              "【LocalVolatileCache.process】 - eventType:【NodeDataChanged】.Changed node path is:【{}】,new info is :【{}】 ",
              path, newInfo);
          if (Objects.nonNull(changedCacheId) && !Objects.equals("", changedCacheId)) {
            LocalDateTime reloadTime = LocalDateTime.now();
            if( Objects.nonNull(temp) && Objects.nonNull(temp.getVersionTimestamp()) ){
              reloadTime = temp.getVersionTimestamp();
            }
            reload(changedCacheId,reloadTime);
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
        try {
          String createdInfo = new String(this.zk.getData(path, this, null),
              Charset.forName(LVCCConstant.CHAR_SET));
          LOG.info(
              "【LocalVolatileCache.process】 - eventType:【NodeCreated】.Created node path is:【{}】,created info is :【{}】 ",
              path, createdInfo);
          if (Objects.nonNull(changedCacheId) && !Objects.equals("", changedCacheId)) {
            get(changedCacheId);
          }
        } catch (KeeperException e) {
          LOG.error(
              "【LocalVolatileCache.process】-NodeCreated,when get created info from zk happend KeeperException :",
              e);
        } catch (InterruptedException e) {
          LOG.error(
              "【LocalVolatileCache.process】-NodeCreated,when get created info from zk happend InterruptedException :",
              e);
        }
        break;
      case NodeDeleted:
        LOG.info(
            "【LocalVolatileCache.process】 - eventType:【NodeDeleted】.Deleted node path is :【{}】",
            path);
        if (Objects.nonNull(changedCacheId) && !Objects.equals("", changedCacheId)) {
          Cache temp = new Cache();
          temp.setId(changedCacheId);
          remove(temp);
        }
        break;
      case NodeChildrenChanged:
        if( localVolatileConfig.getSensitiveAll() ){
          List<String> childNode = getAllExistCacheNode();
          LOG.info(
              "【LocalVolatileCache.process】 - eventType:【NodeChildrenChanged】.parent path is :【{}】.Current child node is :【{}】.Ready to manage.",
              path,JSON.toJSON(childNode));
          for (String el : childNode){
            get(el);
          }
        }else{
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
  private void zkCheck(String methodDesc) {
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
  private void cacheProcessorCheck(String methodDesc) {
    if (isNull(cacheProcessor)) {
      if (Objects.isNull(cacheProcessor)) {
        throw new ZBJException(String.format("【%s】 -cacheProcessor can not be null !", methodDesc));
      }
    }
  }

}
