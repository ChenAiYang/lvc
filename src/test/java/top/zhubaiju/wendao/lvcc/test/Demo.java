package top.zhubaiju.wendao.lvcc.test;

import com.alibaba.fastjson.JSON;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.LockSupport;
import top.zhubaiju.lvc.Cache;
import top.zhubaiju.lvc.LocalVolatileCache;

public class Demo {

  public static void main(String[] args) {

    LocalVolatileCache cache = new LocalVolatileCache();
    cache = new LocalVolatileCache();
    cache.setZkURL("127.0.0.1:2181");
    cache.setCluster(true);
    cache.setCachePro(new KeywordCacheHandler());
    cache.init();

    Thread t = new Thread(new Task(cache));
    t.start();
    LocalVolatileCache cache2 = new LocalVolatileCache();
    cache2 = new LocalVolatileCache();
    cache2.setZkURL("127.0.0.1:2181");
    cache2.setCluster(true);
    cache2.setCachePro(new KeywordCacheHandler());
//    cache2.init();

    Thread t2 = new Thread(new Task(cache2));
//    t2.start();

    while (true) {
      //程序不停止
    }

  }

  static class Task implements Runnable {

    LocalVolatileCache localVolatileCache;
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Task(LocalVolatileCache cache) {
      this.localVolatileCache = cache;
    }

    @Override
    public void run() {

      while (true) {
        Cache cache = localVolatileCache.get("2");
        String info = JSON.toJSONString(cache);
        System.out.println(LocalDateTime.now().format(formatter) + info);
        LockSupport.parkUntil(System.currentTimeMillis() + 1000 * 30);
      }

    }
  }
}
