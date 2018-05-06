package top.zhubaiju.lvcc;

import org.apache.zookeeper.AsyncCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author 人山 create at 2017/4/14 15:13
 */
public class CreateNodeCallBack implements AsyncCallback.StringCallback {

  Logger LOG = LoggerFactory.getLogger(CreateNodeCallBack.class);

  @Override
  public void processResult(int i, String s, Object o, String s1) {
    LOG.info("【CreateNodeCallBack】sync create node callback result,status is :" + i + ",path is:" +
        s + ",info is :" + o + ",node name is :" + s1);
  }
}
