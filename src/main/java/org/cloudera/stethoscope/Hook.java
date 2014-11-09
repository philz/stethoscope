package org.cloudera.stethoscope;

/** 
 * Hooks are injected into methods at their beginnings
 * and ends.
 */
public interface Hook {
  /** A log message sink. */
  public interface Log {
    void log(String s);
  }

  /** Invoked once per hook, before begin() or end() are called. */
  void setLog(Log log);

  /** Invoked at beginning of method. */
  void begin(Object[] args);

  /** Invoked at end of method. */
  void end();
  
  /** Used to get the state of your hook, if you're so inclined. */
  String toString();
}