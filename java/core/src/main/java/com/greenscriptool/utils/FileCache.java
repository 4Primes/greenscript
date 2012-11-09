package com.greenscriptool.utils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FileCache implement
 * 
 * @author greenlaw110@gmail.com
 * @version 1.0, 2010-10-13
 * @since 1.0
 */
public class FileCache {

    private File r_;

    public FileCache(final File root) {
        this.r_ = root;
    }

    Map<List<String>, String> m_ = new HashMap<List<String>, String>();

    private File f_(final String fn) {
        return new File(this.r_, fn);
    }

    public File createTempFile(final List<String> resourceNames, final String extension) {
        // try {
        if (!this.r_.isDirectory() && !this.r_.mkdir()) {
            throw new RuntimeException("cannot create temporary directory for: " + this.r_);
        }

        StringBuilder builder = new StringBuilder();
        for (String resourceName : resourceNames) {
            builder.append(resourceName);
        }

        String key = UUID.nameUUIDFromBytes(builder.toString().getBytes()).toString() + extension;
        // return File.createTempFile("gstmp", extension, r_);
        return new File(this.r_, key);
        // } catch (IOException e) {
        // String msg = "Error create temp file";
        // throw new RuntimeException(msg, e);
        // }
    }

    /**
     * Return cached filename. This method guarantees that file always exists if
     * a non-null value returned
     * 
     * @param key
     * @return filename by key if file exists, null otherwise
     */
    public String get(final List<String> key) {
        String fn = this.m_.get(key);
        if (null == fn) {
            return null;
        }
        if (!this.f_(fn).exists()) {
            this.m_.remove(key);
            return null;
        }
        return fn;
    }

    public String put(final List<String> key, final String fileName) {
        String old = this.remove(key);
        this.m_.put(key, fileName);
        return old;
    }

    public String remove(final List<String> key) {
        String fn = this.m_.remove(key);
        if (null == fn) {
            return null;
        }
        this.delFile_(fn);
        return fn;
    }

    /**
     * Clear cache and corresponding files
     */
    public void clear() {
        for (String fn : this.m_.values()) {
            this.delFile_(fn);
        }
        this.m_.clear();
    }

    private void delFile_(final String fn) {
        File f = this.f_(fn);
        if (f.exists()) {
            if (!f.delete()) {
                f.deleteOnExit();
            }
        }
    }

}
