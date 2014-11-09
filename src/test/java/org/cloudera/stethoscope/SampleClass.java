// Copyright (c) 2013 Cloudera, Inc. All rights reserved.
package org.cloudera.stethoscope;

public class SampleClass {
  public SampleClass() {
    System.out.println("SampleClass() constructor");
  }

  public void sampleMethod() {
    System.out.println("sampleMethod");
  }
  
  public static void sampleStaticMethod() throws Exception {
    Thread.sleep(5000);
  }
  
  public String tricky(int a, boolean b, int[] c, Object d) {
    if (a == 0) {
      return "0";
    } else if (a == -1) {
      throw new RuntimeException();
    } else if (a == 1) {
      return "1";
    } else if (a == 2){
      Object null_ = null;
      if (a == 0) { null_ = 0; }
      // Throws NPE
      null_.toString();
    }
    return null;
  }
}