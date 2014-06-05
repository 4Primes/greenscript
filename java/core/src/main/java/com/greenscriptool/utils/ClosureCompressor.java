package com.greenscriptool.utils;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.jscomp.Result;
import com.greenscriptool.ResourceType;

/**
 * Created by IntelliJ IDEA. User: luog Date: 16/02/12 Time: 12:20 AM To change
 * this template use File | Settings | File Templates.
 */
public class ClosureCompressor implements ICompressor {

    private List<JSSourceFile> externalJavascriptFiles = new ArrayList<JSSourceFile>();

    public ClosureCompressor(final ResourceType type) {
        if (ResourceType.JS != type) {
            throw new IllegalArgumentException("ClosureCompressor does not support CSS compression");
        }
        com.google.javascript.jscomp.Compiler.setLoggingLevel(Level.FINE);
    }

    @Override
    public void compress(final Reader r, final Writer w) throws Exception {
        com.google.javascript.jscomp.Compiler compiler = new com.google.javascript.jscomp.Compiler();
        JSSourceFile file = JSSourceFile
                .fromInputStream("greenscript.js", new ReaderInputStream(r));
        List<JSSourceFile> files = new ArrayList<JSSourceFile>();
        files.add(file);
        System.out.println("!!!--Compressing--!!!");
        CompilerOptions options = new CompilerOptions();
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        Result result = compiler.compile(this.externalJavascriptFiles, files, options);
        if (result.success) {
            w.write(compiler.toSource());
        } else {
            throw new Exception("error compile javascript");
        }
    }
}
