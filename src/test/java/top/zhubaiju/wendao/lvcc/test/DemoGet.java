package top.zhubaiju.wendao.lvcc.test;

import com.alibaba.fastjson.JSON;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.LockSupport;
import top.zhubaiju.lvcc.Cache;
import top.zhubaiju.lvcc.CacheBuilder;
import top.zhubaiju.lvcc.LocalVolatileCache;
import top.zhubaiju.lvcc.support.LocalVolatileCacheProcessor;
import top.zhubaiju.lvcc.support.LocalVolatileConfig;

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
