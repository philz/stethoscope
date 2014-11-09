// Copyright (c) 2013 Cloudera, Inc. All rights reserved.
package org.cloudera.stethoscope;

import java.util.concurrent.ConcurrentHashMap;

public class TracerRuntime {
  private static ConcurrentHashMap<Integer, Hook> hooks = 
      new ConcurrentHashMap<Integer, Hook>();

  public static void register(int id, Hook hook) {
    hooks.put(id, hook);
  }

  public static void deregister(int id) {
    hooks.remove(id);
  }

  public static void begin(int i, Object[] args) {
    System.out.println("hook " + i);
    Hook hook = hooks.get(i);
    if (hook != null) {
      hook.begin(args);
    }
  }

  public static void end(int i) {
    Hook hook = hooks.get(i);
    if (hook != null) {
      hook.end();
    }
  }

  public static void unregister(int id) {
    hooks.remove(id);
  }
}