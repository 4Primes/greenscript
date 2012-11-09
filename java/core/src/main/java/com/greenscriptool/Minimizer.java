package com.greenscriptool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jcoffeescript.JCoffeeScriptCompileException;
import org.jcoffeescript.JCoffeeScriptCompiler;

import com.asual.lesscss.LessEngine;
import com.asual.lesscss.LessException;
import com.greenscriptool.utils.BufferLocator;
import com.greenscriptool.utils.ClosureCompressor;
import com.greenscriptool.utils.FileCache;
import com.greenscriptool.utils.FileResource;
import com.greenscriptool.utils.IBufferLocator;
import com.greenscriptool.utils.ICompressor;
import com.greenscriptool.utils.YUICompressor;

public class Minimizer implements IMinimizer {

    private static Log logger_ = LogFactory.getLog(Minimizer.class);

    private boolean minimize_;
    private boolean compress_;
    private boolean useCache_;
    private boolean inMemory_;
    private boolean processInline_;

    private FileCache cache_ = null;
    private String resourcePath_ = null;
    private String rootDir_ = null;

    private String ctxPath_ = null;
    private String resourceUrlRoot_ = null;
    private String resourceUrlPath_ = null;
    private String cacheUrlPath_ = null;
    private String resourcesParam_ = null;

    private ICompressor compressor_;
    private ResourceType type_;

    private LessEngine less_;
    private JCoffeeScriptCompiler coffee_;

    private void init_(final ICompressor compressor, final ResourceType type) {
        if (null == compressor) {
            throw new NullPointerException();
        }
        this.compressor_ = compressor;
        this.type_ = type;
        this.less_ = new LessEngine();
        this.coffee_ = new JCoffeeScriptCompiler();
    }

    public Minimizer(final ResourceType type) {
        ICompressor compressor = type == ResourceType.CSS ? new YUICompressor(type)
                : new ClosureCompressor(type);
        this.init_(compressor, type);
    }

    @Inject
    public Minimizer(final ICompressor compressor, final ResourceType type) {
        this.init_(compressor, type);
    }

    @Override
    public void enableDisableMinimize(final boolean enable) {
        this.minimize_ = enable || ResourceType.CSS == this.type_;
        if (logger_.isDebugEnabled()) {
            logger_.debug("minimize " + (enable ? "enabled" : "disabled"));
        }
        this.clearCache();
    }

    @Override
    public void enableDisableCompress(final boolean enable) {
        this.compress_ = enable;
        if (logger_.isDebugEnabled()) {
            logger_.debug("compress " + (enable ? "enabled" : "disabled"));
        }
        this.clearCache();
    }

    @Override
    public void enableDisableCache(final boolean enable) {
        this.useCache_ = enable;
        if (logger_.isDebugEnabled()) {
            logger_.debug("cache " + (enable ? "enabled" : "disabled"));
        }
        this.clearCache();
    }

    @Override
    public void enableDisableInMemoryCache(final boolean enable) {
        this.inMemory_ = enable;
        if (logger_.isDebugEnabled()) {
            logger_.debug("in memory cache " + (enable ? "enabled" : "disabled"));
        }
        this.clearCache();
    }

    @Override
    public void enableDisableProcessInline(final boolean enable) {
        this.processInline_ = enable;
        if (logger_.isDebugEnabled()) {
            logger_.debug("inline processing " + (enable ? "enabled" : "disabled"));
        }
    }

    @Deprecated
    public void enableDisableVerifyResource(final boolean verify) {
        // verifyResource_ = verify;
    }

    @Override
    public boolean isMinimizeEnabled() {
        // now css type resource is always minimized
        return this.minimize_ || ResourceType.CSS == this.type_;
    }

    @Override
    public boolean isCompressEnabled() {
        return this.compress_;
    }

    @Override
    public boolean isCacheEnabled() {
        return this.useCache_;
    }

    @Override
    public void setResourceDir(final String dir) {
        this.checkInitialize_(false);
        if (this.rootDir_ == null) {
            throw new IllegalStateException("rootDir need to be intialized first");
        }
        if (dir.startsWith(this.rootDir_)) {
            this.resourcePath_ = dir;
        } else if (dir.startsWith("/")) {
            this.resourcePath_ = this.rootDir_ + dir;
        } else {
            this.resourcePath_ = this.rootDir_ + "/" + dir;
        }
        File f = this.fl_.locate(this.resourcePath_);
        if (!f.isDirectory()) {
            throw new IllegalArgumentException("not a directory");
        }
    }

    @Override
    public void setRootDir(final String dir) {
        this.checkInitialize_(false);
        if (this.fl_ == null) {
            throw new IllegalStateException("file locator need to initialized first");
        }
        this.rootDir_ = dir.endsWith("/") ? dir.substring(0, dir.length() - 1) : dir;
        File f = this.fl_.locate(this.rootDir_);
        if (!f.isDirectory()) {
            throw new IllegalArgumentException("not a directory");
        }
        if (logger_.isDebugEnabled()) {
            logger_.debug(String.format("root dir set to %1$s", dir));
        }
    }

    @Override
    public void setUrlContextPath(String ctxPath) {
        if (null == ctxPath) {
            throw new NullPointerException();
        }
        if (ctxPath.endsWith("/")) {
            ctxPath = ctxPath.substring(0, ctxPath.length() - 1);
        }
        this.ctxPath_ = ctxPath;
    }

    @Override
    public void setCacheDir(final File dir) {
        // comment below as inmemory configuration does not require dir to be
        // exists
        // this is relevant when deploy app on readonly file system like heroku
        // and gae
        // if (!dir.isDirectory() && !dir.mkdir())
        // throw new IllegalArgumentException("not a dir");
        this.checkInitialize_(false);
        this.cache_ = new FileCache(dir);
    }

    @Override
    public void setResourceUrlRoot(String urlRoot) {
        if (this.ctxPath_ == null) {
            throw new IllegalStateException("ctxPath must be intialized first");
        }
        if (!urlRoot.startsWith("/")) {
            throw new IllegalArgumentException("url root must start with /");
        }
        // checkInitialize_(false);
        if (!urlRoot.endsWith("/")) {
            urlRoot = urlRoot + "/";
        }

        this.resourceUrlRoot_ = urlRoot.startsWith(this.ctxPath_) ? urlRoot : this.ctxPath_
                + urlRoot;
        if (logger_.isDebugEnabled()) {
            logger_.debug(String.format("url root set to %1$s", urlRoot));
        }
    }

    @Override
    public void setResourceUrlPath(String urlPath) {
        this.checkInitialize_(false);
        if (null == this.resourceUrlRoot_) {
            throw new IllegalStateException("resourceUrlRoot must be initiated first");
        }
        if (!urlPath.endsWith("/")) {
            urlPath = urlPath + "/";
        }
        if (urlPath.startsWith("/")) {
            this.resourceUrlPath_ = urlPath.startsWith(this.ctxPath_) ? urlPath : this.ctxPath_
                    + urlPath;
        } else {
            this.resourceUrlPath_ = this.resourceUrlRoot_ + urlPath;
        }
        if (logger_.isDebugEnabled()) {
            logger_.debug(String.format("url path set to %1$s", urlPath));
        }
    }

    @Override
    public void setCacheUrlPath(String urlPath) {
        this.checkInitialize_(false);
        if (null == this.resourceUrlRoot_) {
            throw new IllegalStateException("resourceUrlRoot must be initiated first");
        }
        if (!urlPath.endsWith("/")) {
            urlPath = urlPath + "/";
        }
        if (urlPath.startsWith("/")) {
            this.cacheUrlPath_ = urlPath.startsWith(this.ctxPath_) ? urlPath : this.ctxPath_
                    + urlPath;
        } else {
            this.cacheUrlPath_ = this.resourceUrlRoot_ + urlPath;
        }
        if (logger_.isDebugEnabled()) {
            logger_.debug(String.format("cache url root set to %1$s", urlPath));
        }
    }

    @Override
    public void clearCache() {
        this.cache_.clear();
        this.processCache2_.clear();
        this.processCache_.clear();
    }

    private IFileLocator fl_ = FileResource.defFileLocator;

    @Override
    public void setFileLocator(final IFileLocator fileLocator) {
        if (null == fileLocator) {
            throw new NullPointerException();
        }
        this.fl_ = fileLocator;
    }

    private IBufferLocator bl_ = new BufferLocator();

    @Override
    public void setBufferLocator(final IBufferLocator bufferLocator) {
        if (null == bufferLocator) {
            throw new NullPointerException();
        }
        this.bl_ = bufferLocator;
    }

    private IRouteMapper rm_ = null;

    @Override
    public void setRouteMapper(final IRouteMapper routeMapper) {
        if (null == routeMapper) {
            throw new NullPointerException();
        }
        this.rm_ = routeMapper;
    }

    private static final Pattern P_IMPORT = Pattern.compile("^\\s*@import\\s*\"(.*?)\".*");
    private Map<String, Set<File>> importsCache_ = new HashMap<String, Set<File>>();

    private Set<File> imports_(final File file) {
        String key = "less_imports_" + file.getPath() + file.lastModified();

        Set<File> files = this.importsCache_.get(key);
        if (null == files) {
            files = new HashSet<File>();
            try {
                List<String> lines = this.fileToLines_(file);
                for (String line : lines) {
                    Matcher m = P_IMPORT.matcher(line);
                    while (m.find()) {
                        File f = new File(file.getParentFile(), m.group(1));
                        files.add(f);
                        files.addAll(this.imports_(f));
                    }
                }
            } catch (Exception e) {
                if (logger_.isErrorEnabled()) {
                    logger_.error(String.format(
                            "Error occurred getting @imports from resource: $s", file), e);
                }
            }
        }
        return files;
    }

    @Override
    public long getLastModified(final File file) {
        long l = file.lastModified();
        if (ResourceType.CSS == this.type_) {
            // try to get last modified of all @imported files
            for (File f : this.imports_(file)) {
                l = Math.max(l, f.lastModified());
            }
        }
        return l;
    }

    @Override
    public void checkCache() {
        for (List<String> l : this.processCache_.keySet()) {
            for (String s : l) {
                if (this.isCDN_(s)) {
                    continue;
                }
                File f = this.getFileFromURL_(s);
                if (null != f && f.exists()) {
                    long ts1 = this.getLastModified(f);
                    long ts2 = this.lastModifiedCache_.get(f);
                    if (ts1 > ts2) {
                        this.processCache_.remove(l);
                        break;
                    }
                }
            }
        }
    }

    private ConcurrentMap<List<String>, List<String>> processCache_ = new ConcurrentHashMap<List<String>, List<String>>();

    /**
     * A convention used by this minimizer is resource name suffix with
     * "_bundle". For any resource with the name suffix with "_bundle"
     */
    @Override
    public List<String> process(final List<String> resourceNames) {
        this.checkInitialize_(true);
        if (resourceNames.isEmpty()) {
            return Collections.emptyList();
        }
        if (this.minimize_ || ResourceType.CSS == this.type_) {
            if (this.useCache_ && this.processCache_.containsKey(resourceNames)) {
                // !!! cache of the return list instead of minimized file
                List<String> l = this.processCache_.get(resourceNames);
                if (null != l) {
                    return new ArrayList<String>(l);
                }
            }
            // CDN items will break the resource name list into
            // separate chunks in order to keep the dependency order
            List<String> retLst = new ArrayList<String>();
            List<String> tmpLst = new ArrayList<String>();
            for (String fn : resourceNames) {
                if (!this.isCDN_(fn)) {
                    tmpLst.add(fn);
                } else {
                    if (tmpLst.size() > 0) {
                        retLst.add(this.minimize_(tmpLst));
                        tmpLst.clear();
                    }
                    retLst.add(fn);
                }
            }
            if (tmpLst.size() > 0) {
                retLst.add(this.minimize_(tmpLst));
                tmpLst.clear();
            }

            // return minimize_(resourceNames);
            this.processCache_.put(resourceNames, retLst);
            return retLst;
        } else {
            List<String> retLst = this.processWithoutMinimize(resourceNames);
            return retLst;
        }
    }

    private final String getExtension_(final String path) {
        int pos = path.lastIndexOf(".");
        return -1 == pos ? "" : path.substring(pos, path.length());
    }

    private ConcurrentMap<List<String>, List<String>> processCache2_ = new ConcurrentHashMap<List<String>, List<String>>();

    @Override
    public List<String> processWithoutMinimize(final List<String> resourceNames) {
        this.checkInitialize_(true);
        if (resourceNames.isEmpty()) {
            return Collections.emptyList();
        }
        if (this.useCache_ && this.processCache2_.containsKey(resourceNames)) {
            // !!! cache of the return list instead of minimized file
            List<String> l = this.processCache2_.get(resourceNames);
            if (null != l) {
                return new ArrayList<String>(l);
            }
        }
        List<String> l = new ArrayList<String>();
        for (String fn : resourceNames) {
            if (this.isCDN_(fn)) {
                l.add(fn); // CDN resource
            } else {
                String s = fn.replace(this.type_.getExtension(), "");
                File f = null;
                if (s.equalsIgnoreCase("default") || s.endsWith(IDependenceManager.BUNDLE_SUFFIX)) {
                    continue;
                }

                f = this.getFile_(fn);
                if (null == f || !f.isFile()) {
                    continue;
                }

                String ext = this.getExtension_(f.getName());
                fn = fn.endsWith(ext) ? fn : fn + ext;

                fn = this.getUrl_(fn);

                l.add(fn);
            }
        }
        if (l.isEmpty()) {
            logger_.warn("Empty resource list found when processing " + resourceNames);
        }
        this.processCache2_.put(resourceNames, l);
        return l;
    }

    private String compress(final String content) {
        try {
            Reader r = new StringReader(content);
            StringWriter w = new StringWriter();
            this.compressor_.compress(r, w);
            return w.toString();
        } catch (Exception e) {
            logger_.warn("error compress resource", e);
            return content;
        }
    }

    private void compress(final File file, final Writer out) {
        try {
            Reader r = new BufferedReader(new FileReader(file));
            try {
                this.compressor_.compress(r, out);
            } catch (Exception e) {
                logger_.warn("error compress resource " + file.getPath(), e);
                copy_(file, out);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void compress(final String content, final Writer out) {
        Reader r = new StringReader(content);
        try {
            this.compressor_.compress(r, out);
        } catch (Exception e) {
            logger_.warn("error compress resource", e);
            copy_(content, out);
        }
    }

    @Override
    public String processInline(String content) {
        if (!this.processInline_) {
            return content;
        }
        try {
            content = this.preprocess_(content);
            if (this.compress_) {
                return this.compress(content);
            } else {
                return content;
            }
        } catch (StackOverflowError e) {
            logger_.error("fatal error compressing inline content:" + e.getMessage());
            return content;
        } catch (Exception e) {
            logger_.error("error processing inline content", e);
            return content;
        }
    }

    @Override
    public String processStatic(final File file) {
        String content = null;
        try {
            content = this.preprocess_(file);
        } catch (IOException e2) {
            logger_.error("error preprocess static file: " + file.getPath());
            return "";
        }
        try {
            if (this.compress_) {
                return this.compress(content);
            } else {
                return content;
            }
        } catch (StackOverflowError e) {
            logger_.error("fatal error compressing static file: " + file.getName());
            return content;
        } catch (Exception e) {
            logger_.warn("error processing static file: " + file.getPath(), e);
            try {
                return this.fileToString_(file);
            } catch (IOException e1) {
                return "";
            }
        }
    }

    private static String dos2unix_(final String s) {
        return s.replaceAll("\r\n", "\n");
    }

    private String compileLess_(final String s) throws LessException {
        return this.less_.compile(dos2unix_(s)).replace("\\n", "\n");
    }

    private String compileLess_(final File f) throws LessException {
        return this.less_.compile(f).replace("\\n", "\n");
    }

    private String compileCoffee_(final String s) throws JCoffeeScriptCompileException {
        return this.coffee_.compile(s);
    }

    private String compileCoffee_(final File f) throws JCoffeeScriptCompileException, IOException {
        return this.compileCoffee_(this.fileToString_(f));
    }

    public IResource minimize(final String resourceNames) {
        return this.minimize(this.decodeResourceNames(resourceNames));
    }

    private IResource minimize(final List<String> resourceNames) {
        IResource rsrc = this.newCache_(resourceNames);
        Writer out = rsrc.getWriter();
        StringWriter sw = new StringWriter();
        try {
            for (String s : resourceNames) {
                // if (s.startsWith("http:")) l.add(s);
                if (this.isCDN_(s)) {
                    throw new IllegalArgumentException(
                            "CDN resource not expected in miminize method");
                }

                File f = this.getFileFromURL_(s);
                if (null != f && f.exists()) {
                    this.merge_(f, sw, s);
                } else {
                    // possibly a pseudo or error resource name
                }
            }
            String s = sw.toString();
            if (this.lessEnabled_() && this.postMergeLessCompile_()) {
                try {
                    s = this.compileLess_(s);
                } catch (LessException e) {
                    logger_.warn("Error compile less content: " + e.getMessage(), e);
                }
                if (this.compress_) {
                    try {
                        this.compress(s, out);
                    } catch (StackOverflowError e) {
                        logger_.error("fatal error compressing resource: " + e.getMessage());
                    }
                } else {
                    copy_(s, out);
                }
            } else {
                copy_(s, out);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (null != out) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger_.warn("cannot close output in minimizor", e);
                }
            }
        }

        return rsrc;
    }

    private String minimize_(final List<String> resourceNames) {
        FileCache cache = this.cache_;

        if (this.useCache_) {
            String fn = cache.get(resourceNames);
            if (null != fn) {
                if (logger_.isDebugEnabled()) {
                    logger_.debug("cached file returned: " + fn);
                }
                return this.cacheUrlPath_ + fn;
            }
        }

        IResource rsrc = this.minimize(resourceNames);

        String fn = rsrc.getKey();
        // filename always cached without regarding to cache setting
        // this is a good time to remove previous file
        // Note it's absolutely not a good idea to turn cache off
        // and minimize on in a production environment
        cache.put(resourceNames, fn);

        try {
            StringBuilder builder = new StringBuilder();
            builder.append(this.cacheUrlPath_);
            builder.append(fn);

            if (this.resourcesParam_ != null) {
                String resourcesParamValue = this.encodeResourceNames(resourceNames);
                if (resourcesParamValue != null) {
                    builder.append("?");
                    builder.append(this.resourcesParam_);
                    builder.append("=");
                    builder.append(URLEncoder.encode(resourcesParamValue, "utf8"));
                }
            }
            return builder.toString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String encodeResourceNames(final List<String> resourceNames) {
        StringBuilder builder = new StringBuilder();
        for (String resourceName : resourceNames) {
            resourceName = StringUtils.stripToNull(resourceName);
            if (resourceName != null) {
                if (builder.length() > 0) {
                    builder.append(',');
                }
                if (resourceName.startsWith(this.resourceUrlPath_)) {
                    resourceName = resourceName.substring(this.resourceUrlPath_.length());
                }
                builder.append(resourceName);
            }
        }
        return (builder.length() > 0) ? builder.toString() : null;
    }

    private List<String> decodeResourceNames(final String resourceNames) {
        String[] names = resourceNames.split("[,]");
        if (names.length == 0) {
            return Collections.emptyList();
        }

        List<String> l = new ArrayList<String>(names.length);

        for (String name : names) {
            name = StringUtils.stripToNull(name);
            if (name != null) {
                if (!name.startsWith("/")) {
                    name = this.resourceUrlPath_ + name;
                }
                if (!l.contains(name)) {
                    l.add(name);
                }
            }
        }

        return l;
    }

    public static final String SYS_PROP_LESS_ENABLED = "greenscript.less.enabled";

    private boolean lessEnabled_() {
        if (ResourceType.CSS != this.type_) {
            return false;
        }
        boolean b = Boolean.parseBoolean(System.getProperty(SYS_PROP_LESS_ENABLED, "false"));
        return b;
    }

    public static final String SYS_PROP_COFFEE_ENABLED = "greenscript.coffee.enabled";

    private boolean coffeeEnabled_() {
        if (ResourceType.JS != this.type_) {
            return false;
        }
        boolean b = Boolean.parseBoolean(System.getProperty(SYS_PROP_COFFEE_ENABLED, "false"));
        return b;
    }

    /*
     * replace relative url inside the file content with absolute url. This is
     * because the compressed version file will be put in another folder
     * 
     * @param s the content
     * 
     * @param fn the original file name
     */
    private static final Pattern P_URL = Pattern.compile("url\\(['\"]?([^/'\"][^'\"]*?)['\"]?\\)",
            Pattern.CASE_INSENSITIVE | Pattern.CANON_EQ | Pattern.UNICODE_CASE);

    private String processRelativeUrl_(String s, String fn) throws IOException {
        if (ResourceType.CSS != this.type_) {
            throw new IllegalStateException("not a css minimizer");
        }

        if (this.rm_ != null) {
            fn = this.rm_.route(fn);
        }

        /*
         * Process fn: .../a.* -> .../
         */
        int p = fn.lastIndexOf("/") + 1;
        fn = (0 == p) ? this.resourceUrlPath_ : fn.substring(0, p);

        String prefix;
        if (fn.startsWith("/")) {
            if (fn.startsWith(this.resourceUrlPath_)) {
                prefix = fn;
            } else if (fn.startsWith(this.resourceUrlRoot_)) {
                prefix = fn;
            } else {
                prefix = this.resourceUrlRoot_ + fn.replaceFirst("/", "");
            }
        } else {
            prefix = this.resourceUrlPath_ + fn;
        }

        if (this.rm_ != null) {
            prefix = this.rm_.reverse(prefix);
        }

        try {
            Matcher m = P_URL.matcher(s);
            s = m.replaceAll("url(" + prefix + "$1)");
            return s;
        } catch (Throwable e) {
            System.err.println("Error process relative URL: " + fn);
            e.printStackTrace(System.err);
            return s;
        }
    }

    private String fileToString_(final File f) throws IOException {
        BufferedReader r = new BufferedReader(new FileReader(f));
        String l = null;
        StringBuilder sb = new StringBuilder();
        String ls = System.getProperty("line.separator");
        while ((l = r.readLine()) != null) {
            sb.append(l);
            sb.append(ls);
        }
        r.close();
        return sb.toString();
    }

    private List<String> fileToLines_(final File f) throws IOException {
        BufferedReader r = new BufferedReader(new FileReader(f));
        String l = null;
        List<String> lines = new ArrayList<String>();
        while ((l = r.readLine()) != null) {
            lines.add(l);
        }
        r.close();
        return lines;
    }

    private ConcurrentMap<File, Long> lastModifiedCache_ = new ConcurrentHashMap<File, Long>();

    private void merge_(final File file, final Writer out, final String originalFn) {
        if (logger_.isTraceEnabled()) {
            logger_.trace("starting to minimize resource: " + file.getName());
        }

        this.lastModifiedCache_.put(file, this.getLastModified(file));
        // possibly due to error or pseudo resource name
        try {
            String s = this.preprocess_(file, originalFn);
            if (this.compress_ && (!this.lessEnabled_() || !this.postMergeLessCompile_())) {
                if (logger_.isTraceEnabled()) {
                    logger_.trace(String.format("compressing %1$s ...", file.getName()));
                }
                if (null != s) {
                    this.compress(s, out);
                } else {
                    this.compress(file, out);
                }
            } else {
                if (null != s) {
                    copy_(s, out);
                } else {
                    copy_(file, out);
                }
            }
        } catch (IOException e) {
            logger_.warn("error processing javascript file file " + file.getName(), e);
        }
    }

    private String preprocess_(String s) {
        if (this.lessEnabled_()) {
            try {
                s = this.compileLess_(s);
            } catch (Exception e) {
                logger_.warn("process inline content: " + e.getMessage());
            }
        }
        return s;
    }

    private boolean postMergeLessCompile_() {
        return Boolean.valueOf(System.getProperty("greenscript.lessCompile.postMerge", "false"));
    }

    private String preprocess_(final File file) throws IOException {
        String s = null;
        if (this.lessEnabled_() && !this.postMergeLessCompile_()) {
            try {
                s = this.compileLess_(file);
            } catch (LessException e) {
                logger_.warn(
                        "error compile less file: " + file.getName() + ", error: " + e.getMessage(),
                        e);
            }
        } else {
            if (file.getName().endsWith(".coffee")) {
                try {
                    s = this.coffee_.compile(this.fileToString_(file));
                } catch (JCoffeeScriptCompileException e) {
                    logger_.error("error compile coffee script file", e);
                }
            }
        }
        if (null == s) {
            s = this.fileToString_(file);
        }
        return s;
    }

    private String preprocess_(final File file, final String originalFn) throws IOException {
        String s = null;
        if (this.lessEnabled_() && !this.postMergeLessCompile_()) {
            try {
                s = this.compileLess_(file);
            } catch (LessException e) {
                logger_.warn("error compile less file: " + originalFn + ", error: "
                        + e.getMessage());
            }
        } else if (this.coffeeEnabled_() && file.getName().endsWith(".coffee")) {
            try {
                s = this.compileCoffee_(file);
            } catch (JCoffeeScriptCompileException e) {
                logger_.error("error compile coffee script file", e);
            }
        }
        if (null == s) {
            s = this.fileToString_(file);
        }
        if (ResourceType.CSS == this.type_) {
            s = this.processRelativeUrl_(s, originalFn);
        }
        return s;
    }

    private String getUrl_(final String resourceName) {
        String url = null;

        if (!"".equals(this.ctxPath_) && resourceName.startsWith(this.ctxPath_)) {
            url = resourceName;
        } else if (resourceName.startsWith("/")) {
            String s = this.ctxPath_ + resourceName;
            if (s.startsWith(this.resourceUrlRoot_)) {
                url = s;
            } else {
                url = this.resourceUrlRoot_ + resourceName.substring(1, resourceName.length());
            }
        } else {
            url = this.resourceUrlPath_ + resourceName;
        }

        return (this.rm_ != null) ? this.rm_.reverse(url) : url;
    }

    private File getFileFromURL_(final String url) {
        return this.getFile_((this.rm_ != null) ? this.rm_.route(url) : url);
    }

    private File getFile_(String resourceName) {
        if (resourceName.startsWith("/") && !resourceName.startsWith(this.ctxPath_)) {
            resourceName = this.ctxPath_ + resourceName;
        }
        if (resourceName.startsWith(this.resourceUrlPath_)) {
            resourceName = resourceName.replaceFirst(this.resourceUrlPath_, "");
        } else if (resourceName.startsWith(this.resourceUrlRoot_)) {
            resourceName = resourceName.replaceFirst(this.resourceUrlRoot_, "/");
        }
        String fn = resourceName;
        String path;
        if (fn.startsWith("/")) {
            path = (!fn.startsWith(this.rootDir_)) ? this.rootDir_ + "/" + fn.replaceFirst("/", "")
                    : fn;
        } else {
            path = this.resourcePath_ + "/" + fn;
        }
        for (String ext : this.type_.getAllExtensions()) {
            String p = fn.endsWith(ext) ? path : path + ext;
            File f = this.fl_.locate(p);
            if (null != f) {
                return f;
            }
        }
        return null;
    }

    private static void copy_(final File file, final Writer out) throws IOException {
        if (logger_.isTraceEnabled()) {
            logger_.trace(String.format("merging file %1$s ...", file.getName()));
        }
        copy_(new FileReader(file), out);
    }

    public static void copy_(final Reader in, final Writer out) {
        String line = null;
        BufferedReader r = null;
        try {
            r = new BufferedReader(in);
            PrintWriter w = new PrintWriter(out);
            while ((line = r.readLine()) != null) {
                w.println(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (null != r) {
                try {
                    r.close();
                } catch (IOException e) {/* ignore */
                }
            }
        }
    }

    private static void copy_(final String s, final Writer out) {
        copy_(new StringReader(s), out);
    }

    private IResource newCache_(final List<String> resourceNames) {
        if (this.inMemory_) {
            return this.bl_.newBuffer(resourceNames, this.type_.getExtension());
        } else {
            return new FileResource(this.newCacheFile_(resourceNames));
        }
    }

    private File newCacheFile_(final List<String> resourceNames) {
        String extension = this.type_.getExtension();
        return this.cache_.createTempFile(resourceNames, extension);
    }

    private void checkInitialize_(final boolean initialized) {
        boolean notInited = (this.resourcePath_ == null || this.rootDir_ == null
                || this.resourceUrlPath_ == null || this.cache_ == null || this.cacheUrlPath_ == null);

        if (initialized == notInited) {
            throw new IllegalStateException(initialized ? "minimizer not initialized"
                    : "minimizer already initialized");
        }
    }

    public ResourceType getType() {
        return this.type_;
    }

    private final static Pattern P_CDN_PREFIX = Pattern.compile("^https?:");

    private final boolean isCDN_(final String resourceName) {
        if (null == resourceName) {
            return false;
        }
        Matcher m = P_CDN_PREFIX.matcher(resourceName);
        return m.find();
    }

    public void setResourcesParam(final String resourcesParam_) {
        this.resourcesParam_ = resourcesParam_;
    }

}
