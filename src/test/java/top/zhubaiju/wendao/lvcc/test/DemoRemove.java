package top.zhubaiju.wendao.lvcc.test;

import java.util.concurrent.locks.LockSupport;
import top.zhubaiju.lvcc.LocalVolatileCache;

public class DemoRemove {

  public static void main(String[] args) {
    remove();
  }

  private static void remove() {
    LocalVolatileCache lvc = Demo.init();
    int i = 1 ;
    while (i< 50){
      lvc.remove("cacheKey"+i);
      i++;
      LockSupport.parkUntil(System.currentTimeMillis()+1000*10);
    }
    Demo.stay(lvc);
  }

}
