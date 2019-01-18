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

  static class LocalVolatileCacheProcessorA implements LocalVolatileCacheProcessor {

    @Override
    public void onChanged(String expiredCacheID) {
      JSONObject info = new JSONObject();
      info.put("cacheKey",expiredCacheID);
      info.put("lastOperateTime",new Date());
    }

    @Override
    public void onAdd(String notExistCacheID) {
      JSONObject info = new JSONObject();
      info.put("cacheKey",notExistCacheID);
      info.put("lastOperateTime",new Date());
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
      System.out.println("内存健康情况：" + JSON.toJSONString(lvc.listCacheKey()));
      LockSupport.parkUntil(System.currentTimeMillis() + 1000*10);
    }
  }
}
