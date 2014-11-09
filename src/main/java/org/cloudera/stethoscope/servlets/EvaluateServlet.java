package org.cloudera.stethoscope.servlets;

import java.io.IOException;
import java.lang.reflect.Method;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.abstractmeta.toolbox.compilation.compiler.JavaSourceCompiler;
import org.abstractmeta.toolbox.compilation.compiler.impl.JavaSourceCompilerImpl;

/**
 * Compiles and evaluates submitted code.
 * 
 * Expects a POST request with parameter "code",
 * where the code is a snippet that would be inside
 * of a Java function and returns something.
 * 
 * Object.toString() is called on the returned value.
 */
@SuppressWarnings("serial")
public class EvaluateServlet extends HttpServlet {

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    try {
      JavaSourceCompiler javaSourceCompiler = new JavaSourceCompilerImpl();
      JavaSourceCompiler.CompilationUnit compilationUnit = javaSourceCompiler.createCompilationUnit();
      String javaSourceCode =  "package injected;\n" +
          "public class I {\n" +
          "  public static Object go() throws Exception {\n" +
          req.getParameter("code") +
          "  }" +
          "}";
      compilationUnit.addJavaSource("injected.I", javaSourceCode);
      ClassLoader classLoader = javaSourceCompiler.compile(compilationUnit);
      Class<?> injectedClass = classLoader.loadClass("injected.I");
      Method m = injectedClass.getDeclaredMethod("go");
      Object returned = m.invoke(null);

      resp.getWriter().append(returned.toString());
    } catch (Exception e) {
      resp.getWriter().append(e.toString());
    }
  }
}