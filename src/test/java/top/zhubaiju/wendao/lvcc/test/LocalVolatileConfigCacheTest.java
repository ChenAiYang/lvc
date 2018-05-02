package top.zhubaiju.wendao.lvcc.test;

import com.alibaba.fastjson.JSON;
import java.util.concurrent.locks.LockSupport;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import top.zhubaiju.lvc.Cache;
import top.zhubaiju.lvc.LocalVolatileCache;

/**
 * @author 人山 create at 2018/4/16 0:56
 */


public class LocalVolatileConfigCacheTest {

  private static LocalVolatileCache cache;

  @BeforeClass
  public static void initLocalConfigCache() {
    cache = new LocalVolatileCache();
    cache.setZkURL("127.0.0.1:2181");
    cache.setCluster(true);
    cache.init();
    cache.setCachePro(new KeywordCacheHandler());
  }


  @Test
  public void refreshConfigTest() {
    Cache<String> config = Cache.getInstant("1", "Test", "HI-AA");
    cache.refresh(config);
  }


  @Test
  public void removeConfigTest(){
    Cache<String> config = Cache.getInstant("1", "Test", "HI-AA");
    cache.remove(config);
  }

  @Test
  public void getConfigTest(){
    Cache<String> config = cache.get("2");
    System.out.println(JSON.toJSONString(config));

    LockSupport.parkUntil(System.currentTimeMillis()+1000*60*10);
    Assert.assertNotNull(config);

  }


}