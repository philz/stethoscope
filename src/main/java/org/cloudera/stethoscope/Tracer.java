// Copyright (c) 2013 Cloudera, Inc. All rights reserved.
package org.cloudera.stethoscope;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Rewrites classes/methods to add hooks to them.
 */
public class Tracer implements Hook.Log {
  static final boolean VERBOSE = true;
  private final Instrumentation inst;
  
  // Synchronized on this
  private int curCount = 0;
  private Map<Integer, Trace> traces = Maps.newLinkedHashMap();
  
  // Synchronized on this
  private List<String> logItems = Lists.newArrayList();
  
  public class Trace {
    public int id;
    public Class<?> klass;
    public String method;
    public byte[] oldImplementation;
    public Hook hook;
    public String className;
  }
  
  public class JsonTrace {
    public int id;
    public String klass;
    public String method;
    public String value;
  }
  
  Tracer(Instrumentation inst) {
    this.inst = inst;
  }

  synchronized void create(final String klassName, final String method, final Hook hook) {
    Class<?> klass = findClass(klassName);
    
    final int id = curCount;
    curCount++;
    Trace t = new Trace();
    t.className = klassName;
    t.method = method;
    t.id = id;
    t.klass = klass;

    transformClass(method, klass, id);
    t.hook = hook;
    hookup(id, t.hook);
    
    traces.put(id, t);
    
    log(String.format("Tracer %d for %s.%s %s added.",
        id, klassName, method, hook.getClass().getName()));
  }
  
  public synchronized List<JsonTrace> list() {
    List<JsonTrace> ret = Lists.newArrayList();
    for (Trace trace : traces.values()) {
      JsonTrace j = new JsonTrace();
      j.id = trace.id;
      j.klass = trace.className;
      j.method = trace.method;
      try {
        j.value = trace.hook.toString();
      } catch (Exception e) {
        j.value = "<error: " + e.toString() + ">";
      }
      ret.add(j);
    }
    return ret;
  }
  
  public synchronized void delete(int id) {
    Trace t = traces.remove(id);
    if (t == null) {
      return;
    }
    untransformClass(t.klass, t.oldImplementation);
    unhookup(id, t.hook);

    log(String.format("Tracer %d removed.", id));
  }
  
  private void hookup(int id, Hook hook) {
    TracerRuntime.register(id, hook);
    hook.setLog(this);
  }

  private void unhookup(int id, Hook hook) {
    if (VERBOSE) System.out.println("Removing hook " + id + " " + hook);
    TracerRuntime.unregister(id);
  }

  void transformClass(final String method, Class<?> klass, final int id) {
    ClassFileTransformer transformer = new HookAdditionTransformer(method, id);
    if (VERBOSE) {
      System.out.println("Transforming " + klass.toString());
    }
    transformClass(klass, transformer);
  }

  private void transformClass(Class<?> klass, ClassFileTransformer transformer) {
    inst.addTransformer(transformer, true);
    try {
      inst.retransformClasses(klass);
    } catch (UnmodifiableClassException e) {
      throw new RuntimeException(e);
    } finally {
      inst.removeTransformer(transformer);
    }
  }
  
  void untransformClass(Class<?> klass, final byte[] old) {
    ClassFileTransformer transformer = new ClassFileTransformer() {
      @Override
      public byte[] transform(ClassLoader arg0, String arg1, Class<?> arg2,
          ProtectionDomain arg3, byte[] arg4) throws IllegalClassFormatException {
        return old;
      }
    };
    transformClass(klass, transformer);
  }

  Class<?> findClass(final String klassName) {
    Class<?> klass = null;
    try {
      klass = Class.forName(klassName);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    return klass;
  }

  public String getAndClearBuffer() {
  	List<String> cur;
	  synchronized(this) {
  	  cur = logItems;
  	  logItems = Lists.newArrayList();
	  }
	  return Joiner.on("\n").join(cur);
	}

  @Override
  public synchronized void log(String s) {
    logItems.add(s);
  }
}