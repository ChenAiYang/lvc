package top.zhubaiju.wendao.lvcc.test;

import com.alibaba.fastjson.JSON;
import java.util.concurrent.locks.LockSupport;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import top.zhubaiju.lvcc.LocalVolatileConfig;
import top.zhubaiju.lvcc.LocalVolatileConfigCache;

/**
 * @author kaltonguo create at 2018/4/16 0:56
 */


public class LocalVolatileConfigCacheTest {

  private static LocalVolatileConfigCache cache;

  @BeforeClass
  public static void initLocalConfigCache() {
    cache = new LocalVolatileConfigCache();
    cache.setZkURL("192.168.1.2:2181");
    cache.setCluster(true);
    cache.init();
  }

  @Test
  public void registerConfigTest() {
    LocalVolatileConfig<String> config = LocalVolatileConfig.getInstant("2", "Test", "HI");
    cache.registerConfig(config);
    LocalVolatileConfig<String> cachedConfig = cache.getLocalConfig("2");
    System.out.println(JSON.toJSONString(cachedConfig));
    Assert.assertNotNull(cachedConfig);

    //等待30分钟
    LockSupport.parkUntil(System.currentTimeMillis()+1000*60*30);
  }

  @Test
  public void refreshConfigTest() {
    LocalVolatileConfig<String> config = LocalVolatileConfig.getInstant("1", "Test", "HI-AA");
    cache.refreshConfig(config);
  }


  @Test
  public void removeConfigTest(){
    LocalVolatileConfig<String> config = LocalVolatileConfig.getInstant("1", "Test", "HI-AA");
    cache.removeConfig(config);
  }

  @Test
  public void getConfigTest(){
    LocalVolatileConfig<String> config = cache.getLocalConfig("2");
    System.out.println(JSON.toJSONString(config));
    Assert.assertNotNull(config);

  }


}