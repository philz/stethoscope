package org.cloudera.stethoscope;


public class TimingHook implements Hook {
  volatile long elapsedNanos = 0L;
  volatile long invocations = 0L;
  
  ThreadLocal<Long> start = new ThreadLocal<Long>() {
    protected Long initialValue() {
      return 0L;
    };
  };

  @Override
  public void begin(Object[] args) {
    start.set(System.nanoTime());
  }

  @Override
  public void end() {
    elapsedNanos += System.nanoTime() - start.get();
    invocations++;
  }
  
  public long getElapsedNanos() {
    return elapsedNanos;
  }
  
  public long getInvocations() {
    return invocations;
  }
  
  public double getAverageDurationSeconds() {
    if (invocations == 0) {
      return 0.0;
    }
    return (double) elapsedNanos / (double) invocations;
  }

@Override
public void setLog(Hook.Log pw) {
}

@Override
public String toString() {
    return String.format("Invocations: %d, Average Duration: %s", getInvocations(), getAverageDurationSeconds());
}
}