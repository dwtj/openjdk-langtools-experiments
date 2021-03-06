package me.dwtj.ex.openjdk.langtools.sub;

import com.sun.tools.javac.processing.JavacFiler;
import me.dwtj.ex.openjdk.langtools.utils.ExperimentProc;
import me.dwtj.java.compiler.runner.CompilationTaskBuilder;
import me.dwtj.java.compiler.runner.CompilationTaskBuilder.StandardJavaFileManagerConfig;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;

import static javax.tools.JavaFileObject.Kind.CLASS;
import static javax.tools.StandardLocation.CLASS_PATH;
import static me.dwtj.java.compiler.runner.CompilationTaskBuilder.StandardJavaFileManagerConfig.makeConfig;
import static me.dwtj.java.compiler.runner.CompilationTaskBuilder.newBuilder;

/**
 * @author dwtj
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SubCompilationProc extends ExperimentProc {

    @Override
    public boolean process() throws Throwable {
        if (roundEnv.processingOver()) {
            noteClassPathOfFileManagerFromToolProvider();
            noteClassPathOfFileManagerFromFiler();
            //trySubCompilation();
        } else {
            trySubCompilation();
        }
        return false;
    }

    private void noteClassPathOfFileManagerFromToolProvider() {
        note("");
        note("Class path of the file manager from the tool provider:");
        StandardJavaFileManager fileManager = ToolProvider.getSystemJavaCompiler()
                                                          .getStandardFileManager(null, null, null);
        // If there are any elements in the class path, throw an assertion error.
        fileManager.getLocation(CLASS_PATH).forEach(this::note);
        note("");
    }

    private void noteClassPathOfFileManagerFromFiler() {
        note("");
        note("Class path of the file manager from the filer:");
        StandardJavaFileManager fileManager = extractFileManager(processingEnv);
        fileManager.getLocation(CLASS_PATH).forEach(this::note);
        note("");
    }

    private void trySubCompilation() throws IOException {
        // The name of the class to be compiled within the sub-compilation.
        String canonicalName = "me.dwtj.ex.openjdk.langtools.sub.SubDriverClass";
        //String binaryName = "L" + canonicalName.replace('.', '/') + ";";

        note("");
        note("Trying sub-compilation...");
        StandardJavaFileManagerConfig config = makeConfig(extractFileManager(processingEnv));
        File tempClassOutputDir = CompilationTaskBuilder.tempDir();
        note("tempClassOutputDir: " + tempClassOutputDir.getAbsolutePath());
        config.setClassOutputDir(tempClassOutputDir);

        JavaCompiler.CompilationTask task = newBuilder()
                .setFileManagerConfig(config)
                .addClass(canonicalName)
                .build();
        task.call();
        note("Sub-compilation complete.");

        note("Trying to copy the sub-compiled class to the annotation processor's `Filer`...");
        // Use another file manager to get the sub-compiled class and pipe its contents to another
        // class file made by the annotation processor's Filer.
        JavaFileObject dest = filer.createClassFile(canonicalName);
        config = makeConfig().addToClassPath(tempClassOutputDir);  // We read a class from here.
        StandardJavaFileManager fileManager = config.config(
            ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null)
        );
        JavaFileObject src = fileManager.getJavaFileForInput(CLASS_PATH, canonicalName, CLASS);
        pipe(src.openInputStream(), dest.openOutputStream());
        note("Finished copying.");

        // TODO: Delete all temporary files on exit.
        note("");
    }

    /**
     * Taken from <a href="http://stackoverflow.com/a/14634903">this StackOverflow answer</a>.
     */
    private void pipe(InputStream is, OutputStream os) throws IOException {
        int n;
        byte[] buffer = new byte[1024];
        while ((n = is.read(buffer)) > -1) {
            os.write(buffer, 0, n);   // Don't allow any extra bytes to creep in, final write
        }
        os.close();
    }

    /**
     * Use reflection to extract the file manager instance encapsulated within the {@link Filer}
     * provided via the annotation processor's {@link ProcessingEnvironment}.
     */
    private static StandardJavaFileManager extractFileManager(ProcessingEnvironment procEnv) {
        try {
            Field fileManager = JavacFiler.class.getDeclaredField("fileManager");
            fileManager.setAccessible(true);
            return (StandardJavaFileManager) fileManager.get(procEnv.getFiler());
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void copyStandardLocations(StandardJavaFileManager src,
                                              StandardJavaFileManager sink) {
        for (StandardLocation l : StandardLocation.values()) {
            try {
                sink.setLocation(l, src.getLocation(l));
            } catch (IOException ex) { throw new AssertionError(ex); }
        }
    }
}
