package org.cloudera.stethoscope;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.TraceClassVisitor;

public class HookAdditionTransformer implements ClassFileTransformer {
  private static final class HookAdditionClassVisitor extends ClassVisitor {
    private final String method;
    private final int id;

    private HookAdditionClassVisitor(int arg0, ClassVisitor arg1,
        String method, int id) {
      super(arg0, arg1);
      this.method = method;
      this.id = id;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] strings) {
      MethodVisitor mv = cv.visitMethod(access, name, desc, signature, strings);
      if (VERBOSE) System.out.println("Hey " + name + " " + method);
      if (name.equals(method)) {
        if (VERBOSE) System.out.println("Hey, doing some tracing here.");
        return new HookAdditionMethodVisitor(id, Opcodes.ASM4, mv, access, name, desc);
      } else {
        return mv;
      }
    }
  }

  /** 
   * Adds begin() and end() calls.
   * 
   * See http://asm.ow2.org/current/asm-transformations.pdf.
   */
  final static class HookAdditionMethodVisitor extends AdviceAdapter {
    private static final java.lang.reflect.Method TRACE_BEGIN_METHOD;
    static {
      try {
        TRACE_BEGIN_METHOD = TracerRuntime.class.getMethod(
            "begin", Integer.TYPE, Object[].class);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
    private Label startFinally = new Label();
    private int id;
  
    HookAdditionMethodVisitor(int id, int api, MethodVisitor mv, 
          int acc, String name, String desc) {
      super(api, mv, acc, name, desc);
      this.id = id;
    }
    
    public void visitCode() { 
      super.visitCode(); 
      mv.visitLabel(startFinally); 
    } 
    
    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
      Label endFinally = new Label(); 
      mv.visitTryCatchBlock(startFinally, endFinally, endFinally, null); 
      mv.visitLabel(endFinally); 
      onFinally(ATHROW); 
      mv.visitInsn(ATHROW); 
      mv.visitMaxs(maxStack, maxLocals); 
    }
    
    protected void onMethodExit(int opcode) { 
      if(opcode!=ATHROW) { 
        onFinally(opcode); 
      } 
    } 

    @Override
    protected void onMethodEnter() {
      push(id);
  
      // Create an Object[] with all the arguments onto the stack.
      loadArgArray();
      invokeStatic(Type.getType(TracerRuntime.class), 
          Method.getMethod(TRACE_BEGIN_METHOD));
      
      

      /*
      super.visitTypeInsn(Opcodes.ANEWARRAY, Type.getInternalName(Object.class));
        for (int i = 0; i < args; ++i) {
          super.visitInst(OpCodes.DUP);
          if (args[i].get)
          super.visitIntInsn(Opcodes.SIPUSH, i); // could be iconst_X for 0 <= x <= 5    
          super.visitIntInsn(Opcodes.ALOAD, i);
          super.visitInsn(Opcodes.AASTORE);
        }
      
      
          // Method.getMethod("begin"));
      /*
      super.visitMethodInsn(Opcodes.INVOKESTATIC, 
          Type.getInternalName(TracerRuntime.class), "begin", 
          Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE));
          */
    }
    
    private void onFinally(int opcode) { 
      super.visitIntInsn(Opcodes.SIPUSH, id);
      super.visitMethodInsn(Opcodes.INVOKESTATIC, 
          Type.getInternalName(TracerRuntime.class), "end", 
          Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE));
    }
  }

  private final static boolean VERBOSE = Tracer.VERBOSE;
  private final String method;
  private final int id;

  public HookAdditionTransformer(final String method, final int id) {
    this.method = method;
    this.id = id;
  }
  
  public byte[] transform(ClassLoader l, String name, Class<?> c,
      ProtectionDomain d, byte[] b)
          throws IllegalClassFormatException {
    return classTransform(method, id, b);
  }

  /** Decompiles and prints class represented by bytes. */
  public static void printClass(PrintStream ps, byte[] bytes) {
    ps.println(decompileClass(bytes));
  }

  public static String decompileClass(byte[] bytes) {
    return decompileClass(new ClassReader(bytes));
  }

  public static String decompileClass(ClassReader cr) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    TraceClassVisitor cv = new TraceClassVisitor(pw);
    cr.accept(cv, 0x0);
    pw.close();
    
    return sw.toString();
  }

  public static String decompile(String klass) throws IOException {
  	return decompileClass(new ClassReader(klass));
  }

  /**
   * Workhorse method that injects TracerRuntime.begin() and end()
   * invocations into appropriate method.
   */
  static byte[] classTransform(final String method, final int id, byte[] b) {
    if (VERBOSE) {
      System.out.println("Transforming2 " + id);
      System.out.println("original");
      printClass(System.err, b);
    }
  
    ClassReader cr = new ClassReader(b);
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    
    ClassVisitor cv = new HookAdditionClassVisitor(Opcodes.ASM4, cw, method, id);
    cr.accept(cv, ClassReader.SKIP_FRAMES /* get recomputed anyway */);
    byte[] transformed = cw.toByteArray();
  
    System.err.println("transformed");
    printClass(System.err, transformed);
  
    return transformed;
  }
}