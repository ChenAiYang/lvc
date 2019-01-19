package top.zhubaiju.wendao.lvcc.test;

import top.zhubaiju.lvcc.LocalVolatileCache;

public class DemoRemove {

  public static void main(String[] args) {
    remove();
  }

  private static void remove() {
    LocalVolatileCache lvc = Demo.init();

    Demo.stay(lvc);
  }

}
