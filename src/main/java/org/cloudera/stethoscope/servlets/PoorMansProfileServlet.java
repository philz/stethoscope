// Copyright (c) 2011 Cloudera, Inc. All rights reserved.
package org.cloudera.stethoscope.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Poor Man's Profiler.
 * 
 * Inspired by shell scripts involving awk and jstack, as well
 * as tlipcon's version of the same, this implements a very
 * simple profiler by capturing stack traces and counting
 * how often they occur.
 */
@SuppressWarnings("serial")
public class PoorMansProfileServlet extends HttpServlet {
  private static final int MAX_DEPTH = 256;
  private ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
  
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/html");
    resp.setStatus(HttpServletResponse.SC_OK);
    PrintWriter w = resp.getWriter();
    w.println(
        "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML//EN\">\n" +
        "<html><head><title>Poor Man's Profiler</title></head>\n" +
        "<body><h1>Poor Man's Profiler</h1><form action=\"#\" method=\"POST\">\n" +
        "Sleep (milliseconds): <input name=\"interval\" value=10></input><br/>" +
        "Iterations: <input name=\"iterations\" value=1000></input><br/>" +
        "Thread Name Filter (regex): <input name=\"thread_filter\"></input>" +
        "<input type=\"submit\">");
    w.flush();
  }
  
  private static class Item implements Comparable<Item> {
    int count = 0;
    String ref = null;
    Set<String> threads = new HashSet<String>();
    
    @Override
    public int compareTo(Item o) {
      return o.count - this.count;
    }
  }
  
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    int interval = Integer.parseInt(req.getParameter("interval"));
    int iterations = Integer.parseInt(req.getParameter("iterations"));
    Pattern threadFilter = Pattern.compile(req.getParameter("thread_filter"));
    int threadSamples = 0;
    long currentThreadId = Thread.currentThread().getId();
    
    Map<String, Item> counts = new HashMap<String, Item>();
    for (int i = 0; i < iterations; ++i) {
      long[] tids = threadBean.getAllThreadIds();
      ThreadInfo[] infos = threadBean.getThreadInfo(tids, MAX_DEPTH);
      for (ThreadInfo info : infos) {
        if (info == null) {
          continue;
        }
        if (info.getThreadId() == currentThreadId) {
          // Skip the profiling thread.
          continue;
        }
        if (threadFilter.matcher(info.getThreadName()).find()) {
          StringBuilder sb = new StringBuilder();
          sb.append("Thread State: ");
          sb.append(info.getThreadState());
          sb.append("\n");
          for (StackTraceElement frame : info.getStackTrace()) {
            sb.append(frame.toString());
            sb.append("\n");
          }
          String ref = sb.toString();
          Item c = counts.get(ref);
          if (c == null) {
            c = new Item();
            c.ref = ref;
            counts.put(ref, c);
          }
          c.count++;
          c.threads.add(info.getThreadName());
          threadSamples++;
        }
      }
      try {
        Thread.sleep(interval);
      } catch (InterruptedException e) {
        Thread.interrupted();
      }
    }

    
    resp.setContentType("text/html");
    resp.setStatus(HttpServletResponse.SC_OK);
    PrintWriter w = resp.getWriter();
    w.println(
        "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML//EN\">\n" +
        "<html><head><title>Poor Man's Profiler Results</title></head>\n" +
        "<body><h1>Poor Man's Profiler Results</h1>");
    w.println("<table border=1><tr><th>Location</th><th>Threads</th><th>Percent</th></tr>");
    
    ArrayList<Item> items = new ArrayList<Item>(counts.values());
    Collections.sort(items);
    
    for (Item e : items) {
      w.print("<tr><td><pre>");
      w.print(e.ref);
      w.print("</pre></td><td>");
      w.print(e.threads);
      w.print("</td><td>");
      w.print(100 * (float) e.count / (float) threadSamples);
      w.print("%</td></tr>\n");
    }
    w.println("</table>");
  }
}
