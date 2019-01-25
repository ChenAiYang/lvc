package top.zhubaiju.wendao.lvcc.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.util.Date;
import java.util.concurrent.locks.LockSupport;
import top.zhubaiju.common.ZBJException;
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

  static class LocalVolatileCacheProcessorA extends LocalVolatileCacheProcessor {

    @Override
    public void onChanged(String expiredCacheID) {
      System.out.println(" cache key changed , will reload ...");
      JSONObject info = new JSONObject();
      info.put("cacheKey",expiredCacheID);
      info.put("lastOperateTime",new Date());
      System.out.println(" cache key changed , reload success...");
    }

    @Override
    public void onNotExists( LocalVolatileCache lvcc, String cacheKey) {
      System.out.println(" cache key 【"+cacheKey+"】not in current Application , will load ...");
      System.out.println(" loading ...");
      lvcc.commit(cacheKey);
      System.out.println(" load cache key 【"+cacheKey+"】 success!");
    }

    @Override
    public void onAdd(LocalVolatileCache lvcc,String notExistCacheID) {
      System.out.println(" cache key【"+notExistCacheID+"】 commited,but not in current application ,will init ...");
      JSONObject info = new JSONObject();
      info.put("cacheKey",notExistCacheID);
      info.put("lastOperateTime",new Date());
      lvcc.commit(notExistCacheID);
      System.out.println(" cache key 【"+notExistCacheID+"】 commited, init success!");
    }

    @Override
    public void onDeleted(String cacheKey) {
      System.out.println("准备删除："+cacheKey);
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
      System.out.println("LVCC 所管理的所有 cacheKey：" + JSON.toJSONString(lvc.listCacheKey()));
      LockSupport.parkUntil(System.currentTimeMillis() + 1000*10);
    }
  }
}
