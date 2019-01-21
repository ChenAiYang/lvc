package top.zhubaiju.wendao.lvcc.test;

import java.util.concurrent.locks.LockSupport;
import top.zhubaiju.common.ZBJException;
import top.zhubaiju.lvcc.LocalVolatileCache;

public class DemoChangeNotification {

  public static void main(String[] args) {
    notifycationChange();
  }

  private static void notifycationChange() {
    LocalVolatileCache lvc = Demo.init();
    try {
      int i = 1 ;
      while (i< 50){
        lvc.broadcastCacheChange("cacheKey"+i);
        i++;
        LockSupport.parkUntil(System.currentTimeMillis()+1000*3);
      }
      Demo.stay(lvc);
    } catch (ZBJException e) {
      e.printStackTrace();
    }
    Demo.stay(lvc);
  }



}
