package top.zhubaiju.wendao.lvcc.test;

import top.zhubaiju.lvcc.LocalVolatileCache;

public class DemoGet {

  public static void main(String[] args) {
    get();
  }

  private static void get() {
    LocalVolatileCache lvc = Demo.init();
    Cache cache = lvc.get("3");
    Demo.stay(lvc);
  }



}
