package top.zhubaiju.wendao.lvcc.test;

import top.zhubaiju.lvcc.LocalVolatileCache;

public class DemoCommit {

  public static void main(String[] args) {
    commit();
  }

  private static void commit() {
    LocalVolatileCache lvc = Demo.init();
    lvc.commit("cacheKey1");
    Demo.stay(lvc);
  }



}
