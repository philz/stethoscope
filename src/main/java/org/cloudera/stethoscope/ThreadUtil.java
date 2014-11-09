// Copyright (c) 2012 Cloudera, Inc. All rights reserved.
package org.cloudera.stethoscope;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * Thread utility functions. 
 */
public class ThreadUtil {
  
  static private ThreadMXBean threadBean = 
    ManagementFactory.getThreadMXBean();
  static int DEFAULT_STACK_DEPTH=200;
  
  /**
   * Print all of the threads' information and stack traces.
   * 
   * @param stream the stream to
   * @param title a string title for the stack trace
   */
  /**
   * Print all of the thread's information and stack traces.
   * 
   * @param stream the stream to
   * @param title a string title for the stack trace
   */
  public static void printThreadInfo(PrintWriter stream,
                                     String title,
                                     int stackDepth) {
    boolean contention = threadBean.isThreadContentionMonitoringEnabled();
    long[] threadIds = threadBean.getAllThreadIds();
    stream.println("Process Thread Dump: " + title);
    stream.println(threadIds.length + " active threads");
    for (long tid: threadIds) {
      ThreadInfo info = threadBean.getThreadInfo(tid, stackDepth);
      if (info == null) {
        stream.println("  Inactive");
        continue;
      }
      stream.println("Thread " + 
                     getTaskName(info.getThreadId(),
                                 info.getThreadName()) + ":");
      Thread.State state = info.getThreadState();
      stream.println("  State: " + state);
      stream.println("  Blocked count: " + info.getBlockedCount());
      stream.println("  Waited count: " + info.getWaitedCount());
      if (contention) {
        stream.println("  Blocked time: " + info.getBlockedTime());
        stream.println("  Waited time: " + info.getWaitedTime());
      }
      if (state == Thread.State.WAITING) {
        stream.println("  Waiting on " + info.getLockName());
      } else  if (state == Thread.State.BLOCKED) {
        stream.println("  Blocked on " + info.getLockName());
        stream.println("  Blocked by " + 
                       getTaskName(info.getLockOwnerId(),
                                   info.getLockOwnerName()));
      }
      stream.println("  Stack:");
      for (StackTraceElement frame: info.getStackTrace()) {
        stream.println("    " + frame.toString());
      }
    }
    stream.flush();
  }
  
  private static String getTaskName(long id, String name) {
    if (name == null) {
      return Long.toString(id);
    }
    return id + " (" + name + ")";
  }
  
  public static void printThreadInfo(PrintWriter stream, String title) {
    printThreadInfo(stream, title, DEFAULT_STACK_DEPTH);
  }

  static public String printStacks(String title) {

      StringWriter sw = new StringWriter();
      PrintWriter stream = new PrintWriter(sw, true);

      printThreadInfo(stream, title);
      return sw.toString();
  }
}
