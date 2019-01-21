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
      int i = 1;
      while( i <= 15 ){
        System.out.println("ready broad");
        lvc.broadcastCacheChange("cacheKey"+i);
        i++;
      }
    } catch (ZBJException e) {
      e.printStackTrace();
    }
    Demo.stay(lvc);
  }



}
