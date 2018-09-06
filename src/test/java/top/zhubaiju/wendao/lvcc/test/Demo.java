package top.zhubaiju.wendao.lvcc.test;

import com.alibaba.fastjson.JSON;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.LockSupport;
import org.apache.zookeeper.KeeperException;
import top.zhubaiju.common.ZBJException;
import top.zhubaiju.lvcc.Cache;
import top.zhubaiju.lvcc.CacheBuilder;
import top.zhubaiju.lvcc.LocalVolatileCache;
import top.zhubaiju.lvcc.LocalVolatileConfig;
import top.zhubaiju.lvcc.support.LocalVolatileCacheProcessor;

public class Demo {

  static LocalVolatileCache init(){
    LocalVolatileCache lvc = new LocalVolatileCache();
    LocalVolatileConfig config = Demo.initConfig();
    LocalVolatileCacheProcessor cacheProcessor = Demo.initCacheProcessor();
    lvc.setLocalVolatileConfig(config);
    lvc.setCacheProcessor(cacheProcessor);
    try {
      lvc.init();
    } catch (ZBJException e) {
      e.printStackTrace();
    }
    return lvc;
  }

  static LocalVolatileConfig initConfig() {
    LocalVolatileConfig config = new LocalVolatileConfig();
    config.setAuthF("aaa");
    config.setAuthP("bbb");
    config.setClusterSwitch(true);
    config.setLazyLoad(false);
    config.setModule("test");
    config.setNamespace("lvcc");
    config.setSessionTimeOut(5000L);
    config.setZkPath("/STORF");
    config.setZkServerURL("127.0.0.1:2181");
    return config;
  }

  static LocalVolatileCacheProcessor initCacheProcessor() {
    return new LocalVolatileCacheProcessorA();
  }

  static class LocalVolatileCacheProcessorA implements LocalVolatileCacheProcessor {

    @Override
    public Cache processExpired(String expiredCacheID) {
      Cache c = null;
      try {
        c = CacheBuilder.getInstant()
            .build(expiredCacheID, "create-time", "desc", "data:test");
      } catch (ZBJException e) {
        e.printStackTrace();
      }
      return c;
    }

    @Override
    public Cache processNotExist(String notExistCacheID) {
      Cache c = null;
      try {
        c = CacheBuilder.getInstant()
            .build(notExistCacheID, "create-name", "desc", "data:test");
      } catch (ZBJException e) {
        e.printStackTrace();
      }
      return c;
    }

    @Override
    public void lvccExceptionNotifycation(LocalVolatileCache lvcc) {
      int i = 1 ;
      while (true){
        System.out.println("lvccExceptionNotifycation  。。 "+i+" time retry reInit.. ");
        try {
          lvcc.reInit();
          System.out.println("reInit() success !");
          break;
        } catch (ZBJException e) {
          e.printStackTrace();
        } finally {
          i++;
        }

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
