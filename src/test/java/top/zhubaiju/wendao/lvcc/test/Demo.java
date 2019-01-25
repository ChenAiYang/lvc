package top.zhubaiju.wendao.lvcc.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.util.Date;
import java.util.concurrent.locks.LockSupport;
import top.zhubaiju.common.ZBJException;
import top.zhubaiju.lvcc.LocalVolatileCache;
import top.zhubaiju.lvcc.LocalVolatileConfig;
import top.zhubaiju.lvcc.support.LocalVolatileCacheListener;

public class Demo {

  static LocalVolatileCache init(){
    LocalVolatileCache lvc = new LocalVolatileCache();
    LocalVolatileConfig config = Demo.initConfig();
    LocalVolatileCacheListener cacheProcessor = Demo.initCacheProcessor();
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

  static LocalVolatileCacheListener initCacheProcessor() {
    return new LocalVolatileCacheProcessorA();
  }

  static class LocalVolatileCacheProcessorA extends LocalVolatileCacheListener {

    @Override
    public void onChanged(String expiredCacheID) {
      System.out.println("Listener-onChange----------Start");
      System.out.println(" load new cache ....");
      JSONObject info = new JSONObject();
      info.put("cacheKey",expiredCacheID);
      info.put("lastOperateTime",new Date());
      System.out.println(" load success.new cache is : "+ JSON.toJSONStringWithDateFormat(info,"yyyy-MM-dd HH:mm:ss"));
      System.out.println("Listener-onChange----------End");
    }

    @Override
    public void onLose( LocalVolatileCache lvcc, String cacheKey) {
      System.out.println("Listener-onLose----------Start");
      System.out.println(" init cache ....");
      JSONObject info = new JSONObject();
      info.put("cacheKey",cacheKey);
      info.put("lastOperateTime",new Date());
      lvcc.commit(cacheKey);
      System.out.println(" init success. cache is : "+ JSON.toJSONStringWithDateFormat(info,"yyyy-MM-dd HH:mm:ss"));
      System.out.println("Listener-onLose----------End");
    }

    @Override
    public void onAdd(LocalVolatileCache lvcc,String cacheKey) {
      System.out.println("Listener-onAdd----------Start");
      System.out.println(" create new  cache ....");
      JSONObject info = new JSONObject();
      info.put("cacheKey",cacheKey);
      info.put("lastOperateTime",new Date());
      lvcc.commit(cacheKey);
      System.out.println(" create new cache success. cache is : "+ JSON.toJSONStringWithDateFormat(info,"yyyy-MM-dd HH:mm:ss"));
      System.out.println("Listener-onAdd----------End");
    }

    @Override
    public void onDeleted(LocalVolatileCache lvcc, String cacheKey) {
      System.out.println("Listener-onDelete----------Start");
      System.out.println(" delete cache ....");
      lvcc.remove(cacheKey);
      System.out.println(" delete cache success.");
      System.out.println("Listener-onDelete----------End");
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
