package top.zhubaiju.wendao.lvcc.test;

import java.util.concurrent.locks.LockSupport;
import top.zhubaiju.lvcc.LocalVolatileCache;

public class DemoCommit {

  public static void main(String[] args) {
    commit();
  }

  private static void commit() {

    LocalVolatileCache lvc = Demo.init();
    int i = 1 ;
    while (i<= 10){
      lvc.commit("cacheKey"+i);
      i++;
      LockSupport.parkUntil(System.currentTimeMillis()+1000*5);
    }
    Demo.stay(lvc);
  }



}
