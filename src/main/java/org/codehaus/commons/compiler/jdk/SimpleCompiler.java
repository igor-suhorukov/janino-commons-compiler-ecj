
/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2010, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. The name of the author may not be used to endorse or promote products derived from this software without
 *       specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.codehaus.commons.compiler.jdk;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import javax.tools.*;
import javax.tools.JavaFileObject.Kind;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Cookable;
import org.codehaus.commons.compiler.ErrorHandler;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.WarningHandler;

/** The JDK-based implementation of {@link ISimpleCompiler}. */
public
class SimpleCompiler extends Cookable implements ISimpleCompiler {

    protected ClassLoader    parentClassLoader;
    protected ClassLoader    result;
    protected boolean        debugSource;
    protected boolean        debugLines;
    protected boolean        debugVars;
    protected ErrorHandler   optionalCompileErrorHandler;
    protected WarningHandler optionalWarningHandler;

    public SimpleCompiler() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public SimpleCompiler(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }

    @Override public ClassLoader
    getClassLoader() { this.assertCooked(); return this.result; }

    @Override public void
    cook(String optionalFileName, final Reader r) throws CompileException, IOException {
        this.assertNotCooked();

        // Create one Java source file in memory, which will be compiled later.
        JavaFileObject compilationUnit;
        {
            URI uri;
            try {
                uri = new URI(optionalFileName!=null ? "file:///"+optionalFileName : "simplecompiler");
            } catch (URISyntaxException use) {
                throw new RuntimeException(use);
            }
            final String source = Cookable.readString(r);
            compilationUnit = new SimpleJavaFileObject(uri, Kind.SOURCE) {

                @Override public boolean
                isNameCompatible(String simpleName, Kind kind) { return true; }

                @Override public CharSequence
                getCharContent(boolean ignoreEncodingErrors) throws IOException {
                    return source;
                }

                @Override
                public InputStream openInputStream() throws IOException {
                    return new ByteArrayInputStream(source.getBytes());
                }

                @Override public String
                toString() { return String.valueOf(this.uri); }
            };
        }
        JavaCompiler compiler = getJavaCompiler();
        if (compiler == null) {
            throw new CompileException(
                    "JDK Java compiler not available - probably you're running a JRE, not a JDK",
                    null
            );
        }

        // Get the original FM, which reads class files through this JVM's BOOTCLASSPATH and
        // CLASSPATH.
        final StandardJavaFileManager fm = getStandardFileManager(compiler);

        // Wrap it so that the output files (in our case class files) are stored in memory rather
        // than in files.
        final JavaFileManager fileManager = new ForwardingJavaFileManager<JavaFileManager>(
                new ByteArrayJavaFileManager<JavaFileManager>(fm)
        ) {

            @Override public ClassLoader
            getClassLoader(javax.tools.JavaFileManager.Location location) {
                return SimpleCompiler.this.parentClassLoader;
            }
        };

        // Run the compiler.
        try {
            final CompileException[] caughtCompileException = new CompileException[1];
            if (!compiler.getTask(
                    null,                                      // out
                    fileManager,                               // fileManager
                    new DiagnosticListener<JavaFileObject>() { // diagnosticListener

                        @Override public void
                        report(Diagnostic<? extends JavaFileObject> diagnostic) {

                            Location loc = new Location(
                                    null,
                                    (short) diagnostic.getLineNumber(),
                                    (short) diagnostic.getColumnNumber()
                            );
                            String message = diagnostic.getMessage(null) + " (" + diagnostic.getCode() + ")";

                            try {
                                switch (diagnostic.getKind()) {
                                    case ERROR:
                                        if (SimpleCompiler.this.optionalCompileErrorHandler == null) {
                                            throw new CompileException(message, loc);
                                        } else {
                                            SimpleCompiler.this.optionalCompileErrorHandler.handleError(message, loc);
                                        }
                                        break;
                                    case MANDATORY_WARNING:
                                    case WARNING:
                                        if (SimpleCompiler.this.optionalWarningHandler == null) {
                                            ;
                                        } else {
                                            SimpleCompiler.this.optionalWarningHandler.handleWarning(null, message, loc);
                                        }
                                        break;
                                    case NOTE:
                                    case OTHER:
                                    default:
                                        break;

                                }
                            } catch (CompileException ce) {
                                if (caughtCompileException[0] == null) caughtCompileException[0] = ce;
                            }
                        }
                    },
                    getOptions(),
                    getClasses(),
                    Collections.singleton(compilationUnit)     // compilationUnits
            ).call()) {
                if (caughtCompileException[0] != null) throw caughtCompileException[0];
                throw new CompileException("Compilation failed", null);
            }
        } catch (RuntimeException rte) {

            // Unwrap the compilation exception and throw it.
            for (Throwable t = rte.getCause(); t != null; t = t.getCause()) {
                if (t instanceof CompileException) {
                    throw (CompileException) t; // SUPPRESS CHECKSTYLE AvoidHidingCause
                }
                if (t instanceof IOException) {
                    throw (IOException) t; // SUPPRESS CHECKSTYLE AvoidHidingCause
                }
            }
            throw rte;
        }
        // Create a ClassLoader that reads class files from our FM.
        this.result = getJavaFileClassLoader(fileManager, this.parentClassLoader);
    }

    protected StandardJavaFileManager getStandardFileManager(JavaCompiler compiler) {
        return compiler.getStandardFileManager(null, null, null);
    }

    protected JavaCompiler getJavaCompiler() {
        return CompilerUtil.getJavaCompiler();
    }

    protected Iterable<String> getClasses() {
        return null;
    }

    protected List<String> getOptions() {
        return Collections.singletonList(                 // options
                this.debugSource
                        ? "-g:source" + (this.debugLines ? ",lines" : "") + (this.debugVars ? ",vars" : "")
                        : this.debugLines
                        ? "-g:lines" + (this.debugVars ? ",vars" : "")
                        : this.debugVars
                        ? "-g:vars"
                        : "-g:none"
        );
    }

    protected JavaFileManagerClassLoader getJavaFileClassLoader(final JavaFileManager fileManager, final ClassLoader parentClassLoader) {
        return new JavaFileManagerClassLoader(fileManager, parentClassLoader);
    }

    @Override public void
    setDebuggingInformation(boolean debugSource, boolean debugLines, boolean debugVars) {
        this.debugSource = debugSource;
        this.debugLines  = debugLines;
        this.debugVars   = debugVars;
    }

    @Override public void
    setParentClassLoader(ClassLoader optionalParentClassLoader) {
        this.assertNotCooked();
        this.parentClassLoader = (
                optionalParentClassLoader != null
                        ? optionalParentClassLoader
                        : Thread.currentThread().getContextClassLoader()
        );
    }

    /** @deprecated Auxiliary classes never really worked... don't use them. */
    @Deprecated public void
    setParentClassLoader(ClassLoader optionalParentClassLoader, Class<?>[] auxiliaryClasses) {
        this.setParentClassLoader(optionalParentClassLoader);
    }

    @Override public void
    setCompileErrorHandler(ErrorHandler optionalCompileErrorHandler) {
        this.optionalCompileErrorHandler = optionalCompileErrorHandler;
    }

    @Override public void
    setWarningHandler(WarningHandler optionalWarningHandler) {
        this.optionalWarningHandler = optionalWarningHandler;
    }

    /** Throw an {@link IllegalStateException} if this {@link Cookable} is not yet cooked. */
    protected void
    assertCooked() { if (this.result == null) throw new IllegalStateException("Not yet cooked"); }

    /** Throw an {@link IllegalStateException} if this {@link Cookable} is already cooked. */
    protected void
    assertNotCooked() { if (this.result != null) throw new IllegalStateException("Already cooked"); }
}
