package top.zhubaiju.wendao.lvcc.test;

import top.zhubaiju.common.ZBJException;
import top.zhubaiju.lvcc.LocalVolatileCache;

public class DemoChangeNotification {

  public static void main(String[] args) {
    notifycationChange();
  }

  private static void notifycationChange() {
    LocalVolatileCache lvc = Demo.init();
    try {
      lvc.broadcastCacheChange("1");
    } catch (ZBJException e) {
      e.printStackTrace();
    }
    Demo.stay(lvc);
  }



}
