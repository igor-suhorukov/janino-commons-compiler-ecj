package org.codehaus.commons.compiler.jdk;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SimpleClassPathCompiler extends SimpleCompiler {

    private List<URL> classpathUrls;
    private List<String> compilerOptions;

    public SimpleClassPathCompiler(List<URL> classpathUrls) {
        this(new URLClassLoader(classpathUrls.toArray(new URL[classpathUrls.size()]),
                                Thread.currentThread().getContextClassLoader()),
                classpathUrls);
    }

    public SimpleClassPathCompiler(ClassLoader parentClassLoader, List<URL> classpathUrls) {
        super(parentClassLoader);
        this.classpathUrls = classpathUrls;
    }

    public void setCompilerOptions(String... compilerOptions) {
        if(compilerOptions!=null && compilerOptions.length>0){
            this.compilerOptions = Arrays.asList(compilerOptions);
        }
    }

    @Override
    protected List<String> getOptions() {
        List<String> options = super.getOptions();
        ArrayList<String> newOpts = new ArrayList<String>();
        newOpts.addAll(options);
        newOpts.add("-classpath");
        StringBuilder classPathBuilder = new StringBuilder();
        for(URL url: classpathUrls){
            if(classPathBuilder.length()!=0){
                classPathBuilder.append(File.pathSeparatorChar);
            }
            classPathBuilder.append(url.getFile());
        }
        newOpts.add(classPathBuilder.toString());
        if(compilerOptions!=null && !compilerOptions.isEmpty()){
            newOpts.addAll(compilerOptions);
        }
        return newOpts;
    }

    @Override
    protected StandardJavaFileManager getStandardFileManager(JavaCompiler compiler) {
        StandardJavaFileManager standardFileManager = super.getStandardFileManager(compiler);
        Iterable<? extends File> location = standardFileManager.getLocation(StandardLocation.CLASS_PATH);

        ArrayList<File> classpath = new ArrayList<File>();
        for(File file: location){
            classpath.add(file);
        }
        List<File> add = new ArrayList<File>(classpathUrls.size());
        for(URL url: classpathUrls){
            add.add(new File(url.getFile()));
        }
        classpath.addAll(add);
        try {
            standardFileManager.setLocation(StandardLocation.CLASS_PATH, classpath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return standardFileManager;
    }
}
