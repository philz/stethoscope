// Copyright (c) 2013 Cloudera, Inc. All rights reserved.
package org.cloudera.stethoscope;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.net.URLDecoder;

import com.sun.tools.attach.VirtualMachine;

public class StethoscopeMain {
  private Instrumentation inst;

  public StethoscopeMain(Instrumentation inst) {
    this.inst = inst;
  }
  
  public static void main(String[] args) throws Exception {
    boolean selfMode = false;

    if (args.length == 0) {
      usage();
      System.exit(1);
    }
    String pid = args[0];
    Util.addToolsToClasspath();
    if ("self".equals(pid)) {
      // Not guaranteed to be correct, but seems to work.
      pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
      selfMode = true;
    }
    VirtualMachine jvm = Util.attachToPid(pid);
    
    // Gets the current path
    String path = StethoscopeMain.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    String decodedPath = URLDecoder.decode(path, "UTF-8");
    
    String agentArg = args.length <= 1 ? null : args[1];
    jvm.loadAgent(decodedPath, agentArg);
    
    if (selfMode) {
      System.out.println("Not exiting in self mode.");
      Thread.currentThread().join();
    }
  }


  private static void usage() {
    System.err.println("Usage: <pid>\n");
    System.err.println("");
    System.err.println("For testing without a target process, use 'self' as the pid.");
  }

  public static void premain(String agentArgs, Instrumentation inst) throws Exception {
    System.err.println("Agent premain()...");
    new StethoscopeMain(inst).dispatch(agentArgs);
  }
  
  public static void agentmain(String agentArgs, Instrumentation inst) throws Exception {
    System.err.println("Agent agentmain()...");
    new StethoscopeMain(inst).dispatch(agentArgs);
  }

  private void dispatch(String agentArgs) throws Exception {
    web(agentArgs);
  }

  private void web(String x) throws IOException {
    System.err.println("web");
    Util.addToolsToClasspath();
    Util.addSaJdiToClasspath();
    new StethoscopeWebServer(inst, "0.0.0.0", 1234).go();
  }
}