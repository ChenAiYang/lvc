package top.zhubaiju.wendao.lvcc.test;

import top.zhubaiju.lvcc.Cache;
import top.zhubaiju.lvcc.LocalVolatileCache;

public class DemoChangeNotification {

  public static void main(String[] args) {
    notifycationChange();
  }

  private static void notifycationChange() {
    LocalVolatileCache lvc = Demo.init();
    lvc.broadcastCacheChange("1");
    Demo.stay(lvc);
  }



}
