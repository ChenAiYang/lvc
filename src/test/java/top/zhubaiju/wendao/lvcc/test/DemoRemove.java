package top.zhubaiju.wendao.lvcc.test;

import top.zhubaiju.lvcc.Cache;
import top.zhubaiju.lvcc.LocalVolatileCache;

public class DemoRemove {

  public static void main(String[] args) {
    remove();
  }

  private static void remove() {
    LocalVolatileCache lvc = Demo.init();
    Cache cache = lvc.get("1");
//    LockSupport.parkUntil(System.currentTimeMillis()+3000);
    lvc.remove(cache);
    Demo.stay(lvc);
  }

}
