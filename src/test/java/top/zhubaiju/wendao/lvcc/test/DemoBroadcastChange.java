package top.zhubaiju.wendao.lvcc.test;

import java.util.concurrent.locks.LockSupport;
import top.zhubaiju.common.ZBJException;
import top.zhubaiju.lvcc.LocalVolatileCache;

public class DemoBroadcastChange {

  public static void main(String[] args) {
    broadcastChange();
  }

  private static void broadcastChange() {
    LocalVolatileCache lvc = Demo.init();
    try {
      while( true ){
        System.out.println("准备刷新 cacheKey1");
        lvc.broadcastCacheChange("cacheKey1");
        LockSupport.parkUntil(System.currentTimeMillis()+1000*5);
      }
    } catch (ZBJException e) {
      e.printStackTrace();
    }
    Demo.stay(lvc);
  }



}
