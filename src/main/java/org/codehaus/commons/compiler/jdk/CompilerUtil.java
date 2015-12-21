package org.codehaus.commons.compiler.jdk;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.util.ServiceLoader;

/**
 *
 */
public class CompilerUtil {

    public synchronized static JavaCompiler getJavaCompiler(){
        if(!Boolean.getBoolean("skipJavaCompilerService")){
            JavaCompiler javaCompiler = getJavaCompilerService();
            if (javaCompiler != null) return javaCompiler;
        }
        return ToolProvider.getSystemJavaCompiler();
    }

    private static JavaCompiler getJavaCompilerService() {
        try {
            ServiceLoader<JavaCompiler> javaCompilers = ServiceLoader.load(JavaCompiler.class);
            for (JavaCompiler javaCompiler : javaCompilers) {
                return javaCompiler;
            }
        } catch (Exception ignored) {
            //ignore exception
        }
        return null;
    }
}
