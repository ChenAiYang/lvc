package top.zhubaiju.wendao.lvcc.test;

import com.alibaba.fastjson.JSON;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.LockSupport;
import top.zhubaiju.lvcc.Cache;
import top.zhubaiju.lvcc.LocalVolatileCache;
import top.zhubaiju.lvcc.support.LocalVolatileCacheProcessor;
import top.zhubaiju.lvcc.support.LocalVolatileConfig;

public class Demo {

  public static void main(String[] args) {

    LocalVolatileCache cache = new LocalVolatileCache();
    cache = new LocalVolatileCache();
    LocalVolatileConfig config = initConfig();
    LocalVolatileCacheProcessor cacheProcessor = initCacheProcessor();
    cache.setCacheProcessor(cacheProcessor);
    cache.setLocalVolatileConfig(config);
    cache.init();



    while (true) {
      //程序不停止
    }

  }

  static LocalVolatileConfig initConfig(){
    LocalVolatileConfig config = new LocalVolatileConfig();
    config.setAuthF("aaa");
    config.setAuthP("bbb");
    config.setClusterSwitch(true);
    config.setModule("test");
    config.setNamespace("lvcc");
    config.setSessionTimeOut(30000L);
    config.setZkPath("/");
    config.setZkServerURL("127.0.0.1:2181");
    return config;
  }
  static LocalVolatileCacheProcessor initCacheProcessor(){
    return new LocalVolatileCacheProcessorA();
  }
  static class LocalVolatileCacheProcessorA implements LocalVolatileCacheProcessor{

    @Override
    public Cache processExpired(String expiredCacheID) {
      return null;
    }

    @Override
    public Cache processNotExist(String notExistCacheID) {
      return null;
    }
  }

  static class Task implements Runnable {

    LocalVolatileCache localVolatileCache;
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Task(LocalVolatileCache cache) {
      this.localVolatileCache = cache;
    }

    @Override
    public void run() {

      while (true) {

        System.out.println(localVolatileCache.healthInfo());
        Cache c = localVolatileCache.get("2");
        System.out.println(JSON.toJSONString(c));
        LockSupport.parkUntil(System.currentTimeMillis() + 1000 * 30);
        localVolatileCache.broadcastCacheChange(c);
      }

    }
  }
}
