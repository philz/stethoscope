// Copyright (c) 2013 Cloudera, Inc. All rights reserved.
package org.cloudera.stethoscope;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.abstractmeta.toolbox.compilation.compiler.JavaSourceCompiler;
import org.abstractmeta.toolbox.compilation.compiler.impl.JavaSourceCompilerImpl;
import org.cloudera.stethoscope.servlets.EvaluateServlet;
import org.cloudera.stethoscope.servlets.JMXJsonServlet;
import org.cloudera.stethoscope.servlets.PoorMansProfileServlet;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.DefaultServlet;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.resource.Resource;
import org.mortbay.thread.QueuedThreadPool;

import sun.jvm.hotspot.gc_interface.CollectedHeap;
import sun.jvm.hotspot.oops.HeapVisitor;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.runtime.VM;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

/**
 * Starts stethoscope web server and routes requests. 
 */
public class StethoscopeWebServer {
  private final Server server = new Server();
  private final Context context = new Context();
  private final ObjectWriter jsonWriter = 
      new ObjectMapper().writerWithDefaultPrettyPrinter();

  private int port;
  private String host;
  private Instrumentation inst;
  
  public StethoscopeWebServer(Instrumentation inst, String host, int port) {
    this.inst = inst;
    this.host = host;
    this.port = port;
  }

  public void go() {
    QueuedThreadPool pool = new QueuedThreadPool();
    pool.setMinThreads(2); // debug server doesn't need many threads.
    pool.setMaxThreads(10);
    pool.setName("DebugServer(port" + port + ")");
    pool.setDaemon(true);
    server.setThreadPool(pool);
    SocketConnector connector = new SocketConnector();

    connector.setPort(port);
    connector.setHost(host);
    connector.setMaxIdleTime(60000); // 1 min
    server.addConnector(connector);

    context.setContextPath("/");
    context.setBaseResource(Resource.newClassPathResource("/org/cloudera/stethoscope"));
    server.setHandler(context);

    addServlet(new DefaultServlet(), "/");
    addServlet(new DataServlet(), "/json/*");
    addServlet(new TracerServlet(inst), "/tracer/*");
    addServlet(new EvaluateServlet(), "/evaluate");
    addServlet(new PoorMansProfileServlet(), "/poorMansProfiler");
    addServlet(new JMXJsonServlet(), "/jmx");
    
    // addServlet(new LoggerServlet(), "/logging");
    
    try {
      server.start();
    } catch (Exception e) {
      // avoiding logging in this context.
      e.printStackTrace(System.err);
    }
  }
  
  protected void addServlet(final HttpServlet servlet, final String path) {
    context.addServlet(new ServletHolder(servlet), path);
  }
  
  @SuppressWarnings("serial")
  public class DataServlet extends HttpServlet {
    private ObjectWriter jsonWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      PrintWriter out = resp.getWriter();
      
      String pathInfo = req.getPathInfo();
      Object ret = "404 not found";
      if ("/threads".equals(pathInfo)) {
        ret = threads();
      } else if ("/loadedClasses".equals(pathInfo)) {
        ret = loadedClasses();
      } else if ("/stuff".equals(pathInfo)) {
        ret = stuff();
      } else if ("/test".equals(pathInfo)) {
        ret = test();
      } else {
        resp.setStatus(404);
      }
      jsonWriter.writeValue(out, ret);
    }
    
    private Object test() {
      System.err.println("System.err.println");
      System.out.println("System.out.println");
      return "OK";
    }

    private Object threads() {
      return ThreadUtil.printStacks("");
    }
    
    private Object stuff() {
      CollectedHeap foo = VM.getVM().getUniverse().heap();
      final List<String> stuff = Lists.newArrayList();
      VM.getVM().getObjectHeap().iterate(new HeapVisitor() {
        @Override
        public boolean doObj(Oop arg0) {
          if (stuff.size() < 100) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            arg0.printOn(ps);
            ps.close();
            stuff.add(new String(baos.toByteArray(), Charsets.UTF_8));
            return true;
          }
          return false;
        }

        @Override
        public void epilogue() {
        }

        @Override
        public void prologue(long arg0) {
        }
        
      });
      return stuff;
    }
    
    private List<Map<String, String>> loadedClasses() {
      List<Map<String, String>> ret = new ArrayList<Map<String, String>>();
      for (Class<?> c : inst.getAllLoadedClasses()) {
        Map<String, String> data = new HashMap<String, String>();
        data.put("name", c.getName());
        ProtectionDomain protDomain = c.getProtectionDomain();
        boolean put = false;
        if (protDomain != null) {
          CodeSource codeSource = protDomain.getCodeSource();
          if (codeSource != null) {
            data.put("source", codeSource.getLocation().toString());
            put = true;
          } 
        }
        if (!put) {
          data.put("source", null);
        }
        ret.add(data);
      }
      return ret;
    }
  }

  @SuppressWarnings("serial")
  public class TracerServlet extends HttpServlet {
    Tracer tracer;

    public TracerServlet(Instrumentation inst) {
      super();
      tracer = new Tracer(inst);
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      PrintWriter out = resp.getWriter();
      
      String pathInfo = req.getPathInfo();
      Object ret = null;
      if ("/list".equals(pathInfo)) {
        ret = tracer.list();
      } else if ("/decompile".equals(pathInfo)) {
    	  ret = HookAdditionTransformer.decompile(req.getParameter("klass"));
      } else {
        resp.setStatus(404);
      }
      jsonWriter.writeValue(out, ret);
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      PrintWriter out = resp.getWriter();
      String pathInfo = req.getPathInfo();
      Object ret = null;
      if ("/log".equals(pathInfo)) {
        ret = tracer.getAndClearBuffer();
      } else if ("/remove".equals(pathInfo)) {
        tracer.delete(Integer.parseInt(req.getParameter("id")));
      } else if ("/add".equals(pathInfo)) {
        String hookStr = req.getParameter("hook");
        String hookName = req.getParameter("hookName");
        
        Hook h = null;
        if ("timer".equals(hookStr)) {
          h = new TimingHook();
        } else {
          try {
            h = compileHook(hookName, hookStr);
          } catch (Exception e) {
            resp.setStatus(500);
            e.printStackTrace(); // LOG?
            ret = e.getMessage();
          }
        }
        if (h != null) {
          String klass = req.getParameter("klass");
          String method = req.getParameter("method");
          tracer.create(klass, method, h);
  
          ret = "OK";
        }
      } else {
        resp.setStatus(404);
      }
      jsonWriter.writeValue(out, ret);
    }

    private Hook compileHook(String packageAndName, String hookStr) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
      JavaSourceCompiler javaSourceCompiler = new JavaSourceCompilerImpl();
      JavaSourceCompiler.CompilationUnit compilationUnit = javaSourceCompiler.createCompilationUnit();
      String javaSourceCode = hookStr;
      // I can get this using https://code.google.com/p/javaparser/ but it's painful?
      // (or get it from the exception message?)
      compilationUnit.addJavaSource(packageAndName, javaSourceCode);
      // compilationUnit.addClassPathEntry("file:/Users/philip/src/jvm-tools/jtool/target/jtool-0.1-SNAPSHOT.jar");
      System.err.println(Hook.class.getProtectionDomain().getCodeSource().getLocation());
      String foo = Hook.class.getProtectionDomain().getCodeSource().getLocation().getPath();
      System.err.println(foo);
      compilationUnit.addClassPathEntry(foo);
      ClassLoader classLoader = javaSourceCompiler.compile(this.getClass().getClassLoader(), compilationUnit);
      Class<?> injectedClass = classLoader.loadClass(packageAndName);
      return (Hook) injectedClass.newInstance();
    }
  }
}
