package top.zhubaiju.wendao.lvcc.test;

import top.zhubaiju.lvcc.LocalVolatileCache;

public class DemoJustListen {

  public static void main(String[] args) {
    listen();
  }

  private static void listen() {
    LocalVolatileCache lvc = Demo.init();
    Demo.stay(lvc);
  }



}
