package org.codehaus.commons.compiler.jdk;

import org.codehaus.commons.compiler.ISimpleCompiler;
import org.junit.Assert;

import java.lang.reflect.Method;

public class SimpleCompilerTest {

    public void testEcjSimpleCompiler() throws Exception {
        ISimpleCompiler simpleCompiler = new SimpleCompiler();
        simpleCompiler.cook("test/my/Test.java", "package test.my;\npublic class Test{ public double getRes(){return 4.0d * Math.PI;}}");
        Class<?> clazz   = simpleCompiler.getClassLoader().loadClass("test.my.Test");
        Object   instance   = clazz.newInstance();
        Method method   = clazz.getDeclaredMethod("getRes");
        Double   result = (Double) method.invoke(instance);
        Assert.assertEquals(12.56d, result, 0.01);
    }
}
