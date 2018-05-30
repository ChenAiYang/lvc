package top.zhubaiju.lvcc;

import static java.util.Objects.*;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author 人山 create at 2017/4/14 15:13
 */

public final class LocalVolatileCache implements Watcher {

  Logger LOG = LoggerFactory.getLogger(LocalVolatileCache.class);

  private ConcurrentHashMap<String, Cache> cache = new ConcurrentHashMap<>();

  private LocalVolatileCacheProcessor cachePro = new DefaultCacheHandler();

  private static final String CHAR_SET = "UTF-8";


  /**
   * CacheMeta Node Name,default is <code>"APP"</code>
   */
  private String namespace = "APP";


  protected ZooKeeper zk;
  /**
   * CacheMeta Node Parent Path .default is "/"
   */
  private String parentPath = "/";
  private String zkURL;
  private Integer sessionTimeOut = 30000;

  /**
   * Cluster mode flag,true--support cluster,false--a single instant application, default value is
   * false <br/> when the value is true,  Zookeeper will join and keep every app-node cache same.
   */
  private Boolean clusterSwitch = false;


  LocalVolatileCacheProcessor getCachePro() {
    return cachePro;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public void setCachePro(LocalVolatileCacheProcessor cachePro) {
    this.cachePro = cachePro;
  }

  public void init() {
    initZKConnection(this.getZkURL(), this.getSessionTimeOut());
  }

  /**
   * when clusterSwitch true,then  init ZK
   */
  private void initZKConnection(String zkURL, Integer sessionTimeOut) {
    if (clusterSwitch) {
      try {
        //when connect zk, add watcher to notify child node add
        zk = new ZooKeeper(zkURL, sessionTimeOut, this);
        zk.create(this.generateCacheMetaNodePath(),
            namespace.getBytes(CHAR_SET), Ids.OPEN_ACL_UNSAFE,
            CreateMode.PERSISTENT,
            new CreateNodeCallBack(), "Create Node Success");
        zk.getChildren(this.generateCacheMetaNodePath(), this);
      } catch (IOException e) {
        LOG.error("【LocalVolatileCache】 [initZKConnection] Happend IOExcepiton  :", e);
      } catch (InterruptedException e) {
        LOG.error("【LocalVolatileCache】 [initZKConnection] Happend InterruptedException  :", e);
      } catch (KeeperException e) {
        LOG.error("【LocalVolatileCache】 [initZKConnection] Happend KeeperException  :", e);
      }

    }
  }

  /**
   * when try to register a existed cache, it will do nothing
   *
   * @param localCache localCache
   */
  private void register(Cache localCache) {
    // localConfig check
    String check = localCache.check();
    if (!Objects.equals("", check)) {
      throw new IllegalArgumentException(check);
    }
    String configID = localCache.getCacheMeta().getCacheId();
    if (!cache.contains(configID)) {
      cache.put(configID, localCache);
    }

    if (clusterSwitch) {
      registerRemote(localCache);
    }
  }


  /**
   * when try to register a existed cache, it will do nothing
   *
   * @param localCache - localCache
   */
  private void registerRemote(Cache localCache) {
    if (isNull(zk)) {
      LOG.error(
          "【LocalVolatileCache】 - [registerRemote] failure ! ZooKeeper Connection is null !");
      return;
    }
    try {
      Cache.CacheMeta cacheMeta = localCache.getCacheMeta();
      String cacheMetaNodeName = cacheMeta.getCacheId() + "-" + cacheMeta.getCacheName();
      //when register cache meta,add a watcher
      Stat stat = zk.exists(this.generateCacheMetaNodePath() + "/" + cacheMetaNodeName, this);
      if (nonNull(stat)) {
        //exists--do nothing
      } else {
        // not exists
        zk.create(this.generateCacheMetaNodePath() + "/" + cacheMetaNodeName,
            JSON.toJSONString(cacheMeta).getBytes(Charset.forName("utf-8")),
            Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
      }
    } catch (KeeperException e) {
      e.printStackTrace();
      LOG.error("【LocalVolatileCache】[registerRemote] hanpped KeeperException :", e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache】[registerRemote] hanpped InterruptedException :",
          e);
    }

  }


  /**
   * get local config from cache
   *
   * @param cacheId configID
   * @return Cache return a cache
   */
  public Cache get(String cacheId) {
    Cache config = this.cache.get(cacheId);
    if (Objects.isNull(config)) {
      config = cachePro.processNotExist(cacheId);
      if (nonNull(config)) {
        register(config);
      }
    }
    return config;
  }

  /**
   * show local cache health info
   *
   * @return return cache health infomation
   */
  public String healthInfo() {
    JSONObject desc = new JSONObject();
    desc.put("clusterSwitch", this.clusterSwitch);
    if (clusterSwitch) {
      desc.put("zkState", zk.getState());
    }
    desc.put("totalSize(Byte)", JSON.toJSONString(cache.values()).getBytes().length);
    Set<Entry<String, Cache>> entrySet = cache.entrySet();
    for (Entry<String, Cache> el : entrySet) {
      desc.put(el.getKey() + "-" + el.getValue().getCacheMeta().getCacheName(),
          JSON.toJSONString(el.getValue()).getBytes().length);
    }
//    entrySet.forEach(el->{
//      desc.put(el.getKey() + "-" + el.getValue().getCacheMeta().getCacheName(),
//          JSON.toJSONString(el.getValue()).getBytes().length);
//    });
    return desc.toJSONString();
  }

  /**
   * @param localCache localCache
   */
  public void refresh(Cache localCache) {
    String check = localCache.check();
    if (nonNull(check) && !Objects.equals("", check)) {
      throw new IllegalArgumentException(
          "【LocalVolatileCache】 - [refresh] paramter error : " + check);
    }
    String cacheId = localCache.getCacheMeta().getCacheId();
    cache.put(cacheId, localCache);
    if (clusterSwitch) {
      refreshRemote(localCache);
    }
  }

  /**
   * when zk or base-datasource in error,call this method to insure cache right
   *
   * @param localCache localCache
   */
  public void refresh4Local(Cache localCache) {
    String check = localCache.check();
    if (nonNull(check) && !Objects.equals("", check)) {
      throw new IllegalArgumentException(
          "【LocalVolatileCache】 - [refresh] paramter error : " + check);
    }
    String cacheId = localCache.getCacheMeta().getCacheId();
    cache.put(cacheId, localCache);
  }


  /**
   * @param localConfig
   */
  private void refreshRemote(Cache localConfig) {
    if (isNull(zk)) {
      LOG.error(
          "【LocalVolatileCache】 - [refreshRemote] failure ! ZooKeeper Connection is null !");
      return;
    }
    try {
      Cache.CacheMeta cacheMeta = localConfig.getCacheMeta();
      String configNodeName = cacheMeta.getCacheId() + "-" + cacheMeta.getCacheName();
      Stat stat = zk.exists(generateCacheMetaNodePath() +"/"+ configNodeName, false);
      if (nonNull(stat)) {
//        byte[] info = zk.getData(generateCacheMetaNodePath() +"/"+ configNodeName,null,null);
//        String remoteInfo = new String(info,Charset.forName(CHAR_SET));
//        LOG.info("Remote info :{}",remoteInfo);
        String newInfo = JSON.toJSONString(localConfig.getCacheMeta());
        zk.setData(generateCacheMetaNodePath() + "/" + configNodeName, newInfo.getBytes(CHAR_SET),
            stat.getVersion());
      } else {
        LOG.error(
            "【LocalVolatileCache】 - [refreshRemote] path {} not exist !",
            this.getParentPath() + "/" + configNodeName);
      }
    } catch (KeeperException e) {
      e.printStackTrace();
      LOG.error("【LocalVolatileCache】[registerRemote] hanpped KeeperException :", e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache】[registerRemote] hanpped InterruptedException :",
          e);
    } catch (UnsupportedEncodingException e) {
      LOG.error(
          "【LocalVolatileCache】[registerRemote] hanpped UnsupportedEncodingException :",
          e);
    }
  }

  /**
   * @param localCache localConfig
   */
  public void remove(Cache localCache) {
    String cacheId = localCache.getCacheMeta().getCacheId();
    cache.remove(cacheId);
    if (clusterSwitch) {
      removeRemote(localCache);
    }
  }


  /**
   * @param localConfig localConfig
   */
  private void removeRemote(Cache localConfig) {
    if (isNull(zk)) {
      LOG.error(
          "【LocalVolatileCache】 - [removeRemote] failure ! ZooKeeper Connection is null !");
      return;
    }
    try {
      Cache.CacheMeta cacheMeta = localConfig.getCacheMeta();
      String configNodeName = cacheMeta.getCacheId() + "-" + cacheMeta.getCacheName();
      Stat stat = zk.exists(this.getParentPath() + "/" + configNodeName, false);
      if (nonNull(stat)) {
        zk.delete(this.getParentPath() + "/" + configNodeName, stat.getVersion());
      }
    } catch (KeeperException e) {
      e.printStackTrace();
      LOG.error("【LocalVolatileCache】[removeRemote] hanpped KeeperException :", e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache】[removeRemote] hanpped InterruptedException :", e);
    }


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
    if (!clusterSwitch) {
      LOG.info("【LocalVolatileCache】 resetZooKeeperConnection refuse ! clusterSwitch is false ");
      return;
    }
    if (isNull(zkURL) || Objects.equals("", zkURL)) {
      zkURL = this.getZkURL();
    }
    if (isNull(sessionTimeOut)) {
      sessionTimeOut = this.getSessionTimeOut();
    }
    initZKConnection(zkURL, sessionTimeOut);
    Integer leftRetryTimes = retryTimes;
    while (isNull(zk) && leftRetryTimes.intValue() > 0) {
      initZKConnection(zkURL, sessionTimeOut);
    }
  }

  /**
   * listen zk change
   *
   * @param meta -meta
   */
  protected void listenRemove(Cache.CacheMeta meta) {
    this.cache.remove(meta.getCacheId());
  }


  /**
   * CacheMeta changed
   *
   * @param meta -Cache.CacheMeta
   */
  protected void listenRefresh(Cache.CacheMeta meta) {
    Cache newCache = cachePro.processExpired(meta.getCacheId());
    if (nonNull(newCache) && nonNull(newCache.getData())) {
      this.cache.put(meta.getCacheId(), newCache);
    }
  }

  public void setParentPath(String parentPath) {
    this.parentPath = parentPath;
  }

  private String getParentPath() {
    return parentPath;
  }

  String getZkURL() {
    return zkURL;
  }

  public void setZkURL(String zkURL) {
    this.zkURL = zkURL;
  }

  Integer getSessionTimeOut() {
    return sessionTimeOut;
  }

  public void setSessionTimeOut(Integer sessionTimeOut) {
    this.sessionTimeOut = sessionTimeOut;
  }


  public Boolean getClusterSwitch() {
    return clusterSwitch;
  }

  public void setClusterSwitch(Boolean clusterSwitch) {
    this.clusterSwitch = clusterSwitch;
  }

  private String generateCacheMetaNodePath() {
    if ("/".equals(this.getParentPath())) {
      return this.getParentPath() + this.namespace;
    }
    return this.getParentPath() + "/" + this.namespace;
  }




  @Override
  public void process(WatchedEvent watchedEvent) {
    KeeperState keeperState = watchedEvent.getState();
    try {
      zk.getChildren(this.generateCacheMetaNodePath(), this);
    } catch (KeeperException e) {
      LOG.error("【LocalVolatileCache】 [process] happend KeeperException :", e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileCache】 [process] happend InterruptedException :", e);
    }
    EventType eventType = watchedEvent.getType();
    String path = watchedEvent.getPath();
    LOG.info("Listen path:【{}】 have changed, change type is {},state is {}", watchedEvent.getPath(),
        watchedEvent.getType(), keeperState);
    Cache.CacheMeta meta = null;
    if (Objects.nonNull(path) && !Objects.equals("", path) && path
        .startsWith(this.generateCacheMetaNodePath() + "/")) {
      String newInfo = "";
      try {
        newInfo = new String(this.zk.getData(path, this, null), Charset.forName("utf-8"));
        meta = JSON.parseObject(newInfo, Cache.CacheMeta.class);
      } catch (KeeperException e) {
        LOG.error("【LocalVolatileCache】 [process] happend KeeperException :", e);
      } catch (InterruptedException e) {
        LOG.error("【LocalVolatileCache】 [process] happend InterruptedException :", e);
      }
      LOG.info("new info is :{}", newInfo);
    }

    switch (eventType) {
      case None:
        if (keeperState == KeeperState.Expired) {
          this.initZKConnection(this.zkURL, this.sessionTimeOut);
        }
        break;
      case NodeDataChanged:
        listenRefresh(meta);
        break;
      case NodeCreated:
        System.out.println("监听到变化：" + path + "---" + eventType);
        LOG.info(
            "Listen " + watchedEvent.getPath() + " have changed, change type is {},state is {}",
            watchedEvent.getType(), keeperState);
        get(meta.getCacheId());
        break;
      case NodeDeleted:
        listenRemove(meta);
        break;
      case NodeChildrenChanged:
        // just when add a new child node then do next is nessary
        get(meta.getCacheId());
        break;
      default:
        System.out.println("不知道什么类型的监控");
        break;

    }

  }

}
