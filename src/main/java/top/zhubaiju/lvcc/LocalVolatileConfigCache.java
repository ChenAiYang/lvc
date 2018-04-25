package top.zhubaiju.lvcc;

import static java.util.Objects.*;

import com.alibaba.fastjson.JSON;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author 云中鹤 create at 2017/4/14 15:13
 */

public final class LocalVolatileConfigCache implements Watcher {

  Logger LOG = LoggerFactory.getLogger(LocalVolatileConfigCache.class);

  private ConcurrentHashMap<String, LocalVolatileConfig> cache = new ConcurrentHashMap<>();

  private LocalVolatileConfigProcessor expiredConfigHandler = new DefaultConfigHandler();

  private static final String CHAR_SET = "UTF-8";


  /**
   * Config Node Name,default is <code>"APP"</code>
   */
  private String appKey = "APP";


  protected ZooKeeper zk;
  /**
   * Config Node Parent Path .default is "/"
   */
  private String parentPath = "/";
  private String zkURL;
  private Integer sessionTimeOut = 30000;

  /**
   * Cluster mode flag,true--support cluster,false--a single instant application, default value is
   * false <br/> when the value is true,  Zookeeper will join and keep every app-node cache same.
   */
  private Boolean isCluster = false;


  protected LocalVolatileConfigProcessor getExpiredConfigHandler() {
    return expiredConfigHandler;
  }

  public void setExpiredConfigHandler(LocalVolatileConfigProcessor expiredConfigHandler) {
    this.expiredConfigHandler = expiredConfigHandler;
  }

  public void init() {
    initZKConnection(this.getZkURL(), this.getSessionTimeOut());
  }

  /**
   * when isCluster true,then  init ZK
   */
  private void initZKConnection(String zkURL, Integer sessionTimeOut) {
    if (isCluster) {
      try {
        zk = new ZooKeeper(zkURL, sessionTimeOut, this);
        zk.create(this.generateConfigNodePath(),
            appKey.getBytes(CHAR_SET), Ids.OPEN_ACL_UNSAFE,
            CreateMode.PERSISTENT,
            new CreateNodeCallBack(), "Create Node Success");
      } catch (IOException e) {
        LOG.error("【LocalVolatileConfigCache】 [initZKConnection] Happend IOExcepiton  :", e);
      }

    }
  }

  /**
   * when try to register a existed config, it will do nothing
   *
   * @param localConfig localConfig
   */
  public void registerConfig(LocalVolatileConfig localConfig) {
    // localConfig check
    String check = localConfig.check();
    if (!Objects.equals("", check)) {
      throw new IllegalArgumentException(check);
    }
    String configID = localConfig.getConfigMeta().getConfigID();
    if (!cache.contains(configID)) {
      cache.put(configID, localConfig);
    }

    if (isCluster) {
      registerConfigRemote(localConfig);
    }
  }


  /**
   * when try to register a existed config, it will do nothing
   */
  private void registerConfigRemote(LocalVolatileConfig localConfig) {
    if (isNull(zk)) {
      LOG.error(
          "【LocalVolatileConfigCache】 - [registerConfigRemote] failure ! ZooKeeper Connection is null !");
      return;
    }
    try {
      List<String> childNodeNameList = zk.getChildren(this.getParentPath(), false);
      LocalVolatileConfigMeta configMeta = localConfig.getConfigMeta();
      String configNodeName = configMeta.getConfigID() + "-" + configMeta.getConfigName();
      Stat stat = zk.exists(this.generateConfigNodePath() + "/" + configNodeName, false);
      if (nonNull(stat)) {
        //exists
        String info = new String(
            zk.getData(this.generateConfigNodePath() + "/" + configNodeName, this, null),
            CHAR_SET);
        configMeta.setConfigVersion(
            JSON.parseObject(info, LocalVolatileConfigMeta.class).getConfigVersion());
      } else {
        // not exists
        String configVersion = System.currentTimeMillis() + "";
        configMeta.setConfigVersion(configVersion);
        zk.create(this.generateConfigNodePath() + "/" + configNodeName,
            JSON.toJSONString(configMeta).getBytes(CHAR_SET),
            Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

      }
    } catch (KeeperException e) {
      e.printStackTrace();
      LOG.error("【LocalVolatileConfigCache】[registerConfigRemote] hanpped KeeperException :", e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileConfigCache】[registerConfigRemote] hanpped InterruptedException :",
          e);
    } catch (UnsupportedEncodingException e) {
      LOG.error(
          "【LocalVolatileConfigCache】[registerConfigRemote] hanpped UnsupportedEncodingException :",
          e);
    }

  }


  /**
   * get local config from cache
   *
   * @param configID configID
   */
  public LocalVolatileConfig getLocalConfig(String configID) {
    LocalVolatileConfig config = this.cache.get(configID);
    if (Objects.isNull(config)) {
      config = expiredConfigHandler.processNotExist(configID);
      if (nonNull(config)) {
        registerConfig(config);
      }
    }
    return config;
  }

  /**
   * @param localConfig localConfig
   */
  public void refreshConfig(LocalVolatileConfig localConfig) {
    String check = localConfig.check();
    if (nonNull(check) && !Objects.equals("", check)) {
      throw new IllegalArgumentException(
          "【LocalVolatileConfigCache】 - [refreshConfig] paramter error : " + check);
    }
    String configID = localConfig.getConfigMeta().getConfigID();
    cache.put(configID, localConfig);
    if (isCluster) {
      refreshConfigRemote(localConfig);
    }
  }


  /**
   * @param localConfig
   */
  private void refreshConfigRemote(LocalVolatileConfig localConfig) {
    if (isNull(zk)) {
      LOG.error(
          "【LocalVolatileConfigCache】 - [refreshConfigRemote] failure ! ZooKeeper Connection is null !");
      return;
    }
    try {
      LocalVolatileConfigMeta configMeta = localConfig.getConfigMeta();
      configMeta.setConfigVersion(System.currentTimeMillis() + "");
      String configNodeName = configMeta.getConfigID() + "-" + configMeta.getConfigName();
      Stat stat = zk.exists(this.getParentPath() + "/" + configNodeName, false);
      if (nonNull(stat)) {
        String newInfo = JSON.toJSONString(localConfig.getConfigMeta());
        zk.setData(this.getParentPath() + "/" + configNodeName, newInfo.getBytes(CHAR_SET),
            stat.getVersion());
      } else {
        LOG.error(
            "【LocalVolatileConfigCache】 - [refreshConfigRemote] path {} not exist !",
            this.getParentPath() + "/" + configNodeName);
      }
    } catch (KeeperException e) {
      e.printStackTrace();
      LOG.error("【LocalVolatileConfigCache】[registerConfigRemote] hanpped KeeperException :", e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileConfigCache】[registerConfigRemote] hanpped InterruptedException :",
          e);
    } catch (UnsupportedEncodingException e) {
      LOG.error(
          "【LocalVolatileConfigCache】[registerConfigRemote] hanpped UnsupportedEncodingException :",
          e);
    }
  }

  /**
   * @param localConfig localConfig
   */
  public void removeConfig(LocalVolatileConfig localConfig) {
    String configID = localConfig.getConfigMeta().getConfigID();
    cache.remove(configID);
    if (isCluster) {
      removeRemote(localConfig);
    }
  }


  /**
   * @param localConfig localConfig
   */
  private void removeRemote(LocalVolatileConfig localConfig) {
    if (isNull(zk)) {
      LOG.error(
          "【LocalVolatileConfigCache】 - [removeRemote] failure ! ZooKeeper Connection is null !");
      return;
    }
    try {
      LocalVolatileConfigMeta configMeta = localConfig.getConfigMeta();
      configMeta.setConfigVersion(System.currentTimeMillis() + "");
      String configNodeName = configMeta.getConfigID() + "-" + configMeta.getConfigName();
      Stat stat = zk.exists(this.getParentPath() + "/" + configNodeName, false);
      if (nonNull(stat)) {
        zk.delete(this.getParentPath() + "/" + configNodeName, stat.getVersion());
      }
    } catch (KeeperException e) {
      e.printStackTrace();
      LOG.error("【LocalVolatileConfigCache】[removeRemote] hanpped KeeperException :", e);
    } catch (InterruptedException e) {
      LOG.error("【LocalVolatileConfigCache】[removeRemote] hanpped InterruptedException :", e);
    }


  }

  /**
   * remedial measures for connect ZK failure when LocalVolatileConfigCache loading
   *
   * @param zkURL if null,then use original value
   * @param sessionTimeOut sessionTimeOut,then use original value
   * @param retryTimes if null,then default 5
   * @param retryTimesGapMillionSecond if null,then 1000
   */
  public void resetZooKeeperConnection(String zkURL, Integer sessionTimeOut, Integer retryTimes,
      Long retryTimesGapMillionSecond) {
    if (!isCluster) {
      LOG.info("【LocalVolatileConfigCache】 resetZooKeeperConnection refuse ! isCluster is false ");
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
   */
  protected void listenRemove(String configID) {
    this.cache.remove(configID);
  }

  protected void listenAdd(String newConfigID) {
//TODO 需要放开接口，要求调用方实现
  }

  protected void listenRefresh(LocalVolatileConfigMeta meta) {
    LocalVolatileConfig newConfig = expiredConfigHandler.processExpired(meta.getConfigID());
    if (nonNull(newConfig) && nonNull(newConfig.getConfigData())) {
      this.cache.put(meta.getConfigID(), newConfig);
    }
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


  Boolean getCluster() {
    return isCluster;
  }

  public void setCluster(Boolean cluster) {
    isCluster = cluster;
  }


  private String generateConfigNodePath() {
    if ("/".equals(this.getParentPath())) {
      return this.getParentPath() + this.appKey;
    }
    return this.getParentPath() + "/" + this.appKey;
  }


  @Override
  public void process(WatchedEvent watchedEvent) {

    EventType eventType = watchedEvent.getType();
    String path = watchedEvent.getPath();
    System.out.println("检测到变化，节点路径："+watchedEvent.getPath()+"，状态："+watchedEvent.getType());
    switch (eventType) {
      case None:
        break;

      case NodeDataChanged:
        try {
          // register watcher
          String info = new String(this.zk.getData(path, this, null), "UTF-8");
          System.out.println("节点发生变化："+info);
          LocalVolatileConfigMeta meta = JSON.parseObject(info, LocalVolatileConfigMeta.class);
          listenRefresh(meta);
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        } catch (KeeperException e) {
          e.printStackTrace();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        break;
      case NodeCreated:
        break;
      case NodeDeleted:
        break;
      default:
        break;

    }

    watchedEvent.getType();
    watchedEvent.getPath();
//    watchedEvent.
  }

}
