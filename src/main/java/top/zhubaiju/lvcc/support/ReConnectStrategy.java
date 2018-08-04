package top.zhubaiju.lvcc.support;

public class ReConnectStrategy {

  private Integer maxRetryTimes=3;
  private Long intervalMillionSecond=2000L;

  public Integer getMaxRetryTimes() {
    return maxRetryTimes;
  }

  public void setMaxRetryTimes(Integer maxRetryTimes) {
    this.maxRetryTimes = maxRetryTimes;
  }

  public Long getIntervalMillionSecond() {
    return intervalMillionSecond;
  }

  public void setIntervalMillionSecond(Long intervalMillionSecond) {
    this.intervalMillionSecond = intervalMillionSecond;
  }
}
