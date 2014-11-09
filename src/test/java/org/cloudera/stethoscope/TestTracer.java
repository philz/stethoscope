package org.cloudera.stethoscope;

import java.io.IOException;
import java.lang.reflect.Method;

import org.cloudera.stethoscope.Hook;
import org.cloudera.stethoscope.HookAdditionTransformer;
import org.cloudera.stethoscope.TracerRuntime;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public class TestTracer {
  
  public static class TestHook implements Hook {
    int beginCalled;
    int endCalled;
    Object[] lastArgs;
    @Override public void setLog(Log log) { }
    @Override public void begin(Object[] args) { 
      beginCalled++; 
      lastArgs = args;
    }
    @Override public void end() { endCalled++; }
  }

  class MyClassLoader extends ClassLoader {
    public Class<?> defineClass(String name, byte[] b) {
      return defineClass(name, b, 0, b.length);
    }
  }
  
  public void invoke(Class<?> klass, String method, Object[] args) throws Exception {
    String klassName = klass.getName();
    int id = 1;
    byte[] bytes = readClass(klassName);
    byte[] transformed = HookAdditionTransformer.classTransform(method, 1, bytes);
    System.out.format("Transformed %d -> %d bytes.", bytes.length, transformed.length);
    Assert.assertTrue("Expecting code size to increase", 
        bytes.length < transformed.length);
    TestHook h = new TestHook();
    TracerRuntime.register(id, h);
    Class<?> klass2 = new MyClassLoader().defineClass(
        klassName, transformed);
    Object z = klass2.newInstance();
    for (Method m : klass2.getMethods()) {
      // match by name, to avoid users needing
      // to specify all the arguments
      if (m.getName().equals(method)) {
        try {
          m.invoke(z, args);
        } catch (Exception e) {
          // may have been expected
        }
      }
    }
    
    Assert.assertTrue("begin was called", h.beginCalled == 1);
    Assert.assertTrue("end was called", h.endCalled == 1);
    Assert.assertArrayEquals("args were passed", h.lastArgs, args);
    
    TracerRuntime.deregister(id);
    
  }

  @Test
  public void testTracing() throws Exception {
    invoke(SampleClass.class, "sampleMethod", new Object[0]);
    invoke(SampleClass.class, "sampleStaticMethod", new Object[0]);
    //   public String tricky(int a, boolean b, int[] c, Object d) {
    invoke(SampleClass.class, "tricky", new Object[] { -1, true, new int[0], null });
    invoke(SampleClass.class, "tricky", new Object[] { 0, true, new int[0], null });
    invoke(SampleClass.class, "tricky", new Object[] { 1, true, new int[0], null });
    invoke(SampleClass.class, "tricky", new Object[] { 2, true, new int[0], null });
  }

  byte[] readClass(String className) throws IOException {
    ClassWriter cw = new ClassWriter(0);
    ClassReader cr = new ClassReader(className);
    cr.accept(cw, 0);
    byte[] bytes = cw.toByteArray();
    return bytes;
  }
}
