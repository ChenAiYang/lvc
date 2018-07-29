package top.zhubaiju.lvcc.support;

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
import top.zhubaiju.common.LVCCConstant;

/**
 * @author iyoung chen
 */
public class LocalVolatileConfig {

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

  /**
   * when authP and authF have valide value indicate need auth sec   *
   *
   * @return true or false
   */
  public boolean needAuthSec() {
    if (Objects.nonNull(authP) && !Objects.equals("", authP) &&
        Objects.nonNull(authF) && !Objects.equals("", authF)) {
      return true;
    }
    return false;
  }

  /**
   * caculate cache path like : /[zkPath]/[namespace]-[moudle]
   * @return
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
    List<ACL> aclList = new ArrayList<>();

    try {
      Id id = new Id("digest", DigestAuthenticationProvider.generateDigest(authP + ":" + authF));
      Id readId = Ids.ANYONE_ID_UNSAFE;
      ACL acl = new ACL(ZooDefs.Perms.ALL, id);
      ACL aclRead = new ACL(Perms.READ, readId);
      aclList.add(acl);
      aclList.add(aclRead);
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    return aclList;
  }
}
