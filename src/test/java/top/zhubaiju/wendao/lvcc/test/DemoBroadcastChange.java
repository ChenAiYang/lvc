package top.zhubaiju.wendao.lvcc.test;

import top.zhubaiju.common.ZBJException;
import top.zhubaiju.lvcc.LocalVolatileCache;

public class DemoBroadcastChange {

  public static void main(String[] args) {
    broadcastChange();
  }

  private static void broadcastChange() {
    LocalVolatileCache lvc = Demo.init();
    try {
      lvc.broadcastCacheChange("cacheKey1");
    } catch (ZBJException e) {
      e.printStackTrace();
    }
    Demo.stay(lvc);
  }



}
