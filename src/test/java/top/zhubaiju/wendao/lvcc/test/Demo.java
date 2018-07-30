package top.zhubaiju.wendao.lvcc.test;

import com.alibaba.fastjson.JSON;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.LockSupport;
import top.zhubaiju.lvcc.Cache;
import top.zhubaiju.lvcc.CacheBuilder;
import top.zhubaiju.lvcc.LocalVolatileCache;
import top.zhubaiju.lvcc.support.LocalVolatileCacheProcessor;
import top.zhubaiju.lvcc.support.LocalVolatileConfig;

public class Demo {

  static LocalVolatileCache init(){
    LocalVolatileCache lvc = new LocalVolatileCache();
    LocalVolatileConfig config = Demo.initConfig();
    LocalVolatileCacheProcessor cacheProcessor = Demo.initCacheProcessor();
    lvc.setLocalVolatileConfig(config);
    lvc.setCacheProcessor(cacheProcessor);
    lvc.init();
    return lvc;
  }

  static LocalVolatileConfig initConfig() {
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

  static LocalVolatileCacheProcessor initCacheProcessor() {
    return new LocalVolatileCacheProcessorA();
  }

  static class LocalVolatileCacheProcessorA implements LocalVolatileCacheProcessor {

    @Override
    public Cache processExpired(String expiredCacheID) {
      Cache c = CacheBuilder.getInstant()
          .build(expiredCacheID, "create-out-time", "desc", "data:test");
      return c;
    }

    @Override
    public Cache processNotExist(String notExistCacheID) {
      Cache c = CacheBuilder.getInstant()
          .build(notExistCacheID, "create-name", "desc", "data:test");
      return c;
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
        LockSupport.parkUntil(System.currentTimeMillis() + 1000 * 60*3);
        localVolatileCache.broadcastCacheChange(c);
      }

    }
  }

  public static void stay(LocalVolatileCache lvc){
    while (true) {
      String heath = lvc.healthInfo();
      System.out.println("内存健康情况：" + heath);
      LockSupport.parkUntil(System.currentTimeMillis() + 1000*10);
    }
  }
}
