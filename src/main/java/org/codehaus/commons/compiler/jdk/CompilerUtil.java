package org.codehaus.commons.compiler.jdk;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.util.ServiceLoader;

/**
 *
 */
public class CompilerUtil {

    public synchronized static JavaCompiler getJavaCompiler(){
        try {
            ServiceLoader<JavaCompiler> javaCompilers = ServiceLoader.load(JavaCompiler.class);
            for (JavaCompiler javaCompiler : javaCompilers) {
                return javaCompiler;
            }
        } catch (Exception ignored) {
            //ignore exception
        }
        return ToolProvider.getSystemJavaCompiler();
    }
}
