package top.zhubaiju.lvcc.support;

import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooDefs.Perms;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.server.auth.DigestAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.zhubaiju.common.LVCCConstant;

/**
 * @author iyoung chen
 */
public class LocalVolatileConfig {

  Logger LOG = LoggerFactory.getLogger(LocalVolatileConfig.class);

  /**
   * ZK config : the zk server url
   */
  private String zkServerURL;

  /**
   * ZK config : the base path LVCC may need .
   */
  private String zkPath = LVCCConstant.DEFAULT_BASE_ZK_PATHE;

  /**
   * ZK config : security config , username
   */
  private String authP;
  /**
   * ZK config : security config , vcode
   */
  private String authF;
  /**
   * a String flag that application belong.you can think it is "GroupID" in maven domain(GAV:groupid,artifactId,version)
   */
  private String namespace;
  /**
   * a String flag that application have.you can think it is Sub-Module "GroupID" in maven domain(GAV:groupid,artifactId,version)
   */
  private String module;
  /**
   * unit: milliseconds
   */
  private Long sessionTimeOut = 30000L;

  /**
   * if set false, LVCC will load all cache which LVCC-REMOTE(ZK) have managed when init. otherwise, it just load when excute get() method .
   */
  private Boolean lazyLoad = true;

  /**
   * <p>
   * if set false, LVCC in application instant-A will not auto manage a new cache named cache-21 <br/>
   * which application instant-B commit, LVCC manage cahce-21 only happend application instant-A call get() method <br/>
   * </p>
   *
   */
  private Boolean sensitiveAll = true;

  /**
   * If application have more than one instant ,then you need set <code>
   * clusterSwitch
   * </code> true
   */
  private Boolean clusterSwitch = false;

  public String getZkServerURL() {
    return zkServerURL;
  }

  public void setZkServerURL(String zkServerURL) {
    this.zkServerURL = zkServerURL;
  }

  public String getZkPath() {
    return zkPath;
  }

  public void setZkPath(String zkPath) {
    this.zkPath = zkPath;
  }

  public String getAuthP() {
    return authP;
  }

  public void setAuthP(String authP) {
    this.authP = authP;
  }

  public String getAuthF() {
    return authF;
  }

  public void setAuthF(String authF) {
    this.authF = authF;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getModule() {
    return module;
  }

  public void setModule(String module) {
    this.module = module;
  }

  public Long getSessionTimeOut() {
    return sessionTimeOut;
  }

  public void setSessionTimeOut(Long sessionTimeOut) {
    this.sessionTimeOut = sessionTimeOut;
  }

  public Boolean getClusterSwitch() {
    return clusterSwitch;
  }

  public void setClusterSwitch(Boolean clusterSwitch) {
    this.clusterSwitch = clusterSwitch;
  }

  public Boolean getLazyLoad() {
    return lazyLoad;
  }

  public void setLazyLoad(Boolean lazyLoad) {
    this.lazyLoad = lazyLoad;
  }

  public Boolean getSensitiveAll() {
    return sensitiveAll;
  }

  public void setSensitiveAll(Boolean sensitiveAll) {
    this.sensitiveAll = sensitiveAll;
  }

  /**
   * when authP and authF have valide value indicate need auth sec   *
   *
   * @return true or false
   */
  public boolean needAuthSec() {
    if (Objects.isNull(authP) || Objects.equals("", authP)) {
      return false;
    }
    if (Objects.isNull(authF) || Objects.equals("", authF)) {
      return false;
    }
    return true;
  }

  /**
   * caculate cache path like : /[zkPath]/[namespace]-[moudle]
   */
  public String zkCacheNodePath() {
    StringBuilder sbd = new StringBuilder();
    if (Objects.equals(LVCCConstant.DEFAULT_BASE_ZK_PATHE, this.getZkPath())) {
      sbd.append(LVCCConstant.DEFAULT_BASE_ZK_PATHE).append(this.namespace).append("-")
          .append(this.module);
    } else {
      sbd.append(this.getZkPath()).append(LVCCConstant.DEFAULT_BASE_ZK_PATHE).append(this.namespace)
          .append("-").append(this.module);

    }
    return sbd.toString();
  }

  public List<ACL> generateACL() {
    if (!needAuthSec()) {
      return Ids.OPEN_ACL_UNSAFE;
    }
    List<ACL> aclList = new ArrayList<>();
    try {
      Id digestId = new Id("digest", DigestAuthenticationProvider.generateDigest(authP + ":" + authF));
      Id readId = Ids.ANYONE_ID_UNSAFE;
      ACL aclDigest = new ACL(ZooDefs.Perms.ALL, digestId);
      ACL aclRead = new ACL(Perms.READ, readId);
      aclList.add(aclDigest);
      aclList.add(aclRead);
    } catch (NoSuchAlgorithmException e) {
      LOG.error("【LocalVolatileConfig.generateACL】 happend eception :",e);
      aclList = Ids.READ_ACL_UNSAFE;
    }
    return aclList;
  }

  public byte[] auth(){
    if( needAuthSec() ){
      StringBuilder sbd = new StringBuilder();
      sbd.append(authP);
      sbd.append(":");
      sbd.append(authF);
      return sbd.toString().getBytes(Charset.forName(LVCCConstant.CHAR_SET));
    }
    return null;
  }


}
