package com.greenscriptool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.inject.Inject;

/**
 * The implementation of {@link IRenderSession} interface
 * 
 * @author greenlaw110@gmail.com
 * @version 1.0.1, 2010-11-13 add compatibility to play-greenscript v1.1 or
 *          before
 * @version 1.0, 2010-10-15 original version
 * @since 1.0
 */
public class RenderSession implements IRenderSession {

    private IMinimizer m_ = null;

    private IDependenceManager d_ = null;

    private ResourceType type_ = null;

    /**
     * Store resource declared using {@link #declare(String, String, String)}
     */
    private Set<Resource> declared_ = new HashSet<RenderSession.Resource>();

    /**
     * Store all resources that has been loaded (in this session) already
     */
    private Set<String> loaded_ = new HashSet<String>();

    /**
     * Store inline bodies declared
     */
    private SortedMap<Integer, StringBuffer> inlines_ = new TreeMap<Integer, StringBuffer>();

    public ResourceType getResourceType() {
        return this.type_;
    }

    /**
     * Construct an {@link IRenderSession} with an {@link IMinimizer} instance,
     * an {@link IDependenceManager} instance and a {@link ResourceType}
     * 
     * @param minimizer
     * @param depMgr
     * @param type
     */
    @Inject
    public RenderSession(final IMinimizer minimizer, final IDependenceManager depMgr,
            final ResourceType type) {
        if (null == minimizer || null == depMgr) {
            throw new NullPointerException();
        }
        this.m_ = minimizer;
        this.d_ = depMgr;
        this.type_ = type;
    }

    private final void trace(final String s, final Object... args) {
        // s = String.format(s, args);
        // logger_.info(s);
    }

    @Override
    public void declareInline(final String inline, int priority) {
        priority = -1 * priority;
        StringBuffer sb = this.inlines_.get(priority);
        if (null == sb) {
            sb = new StringBuffer();
            this.inlines_.put(priority, sb);
        }
        sb.append("\n").append(inline);
    }

    @Override
    public void declare(final String nameList, String media, String browser) {
        this.d_.processInlineDependency(nameList);
        String[] sa = nameList.split(SEPARATOR);
        media = this.canonical_(media);
        browser = this.canonical_(browser);
        for (String name : sa) {
            this.declared_.add(new Resource(name, media, browser));
        }
    }

    @Override
    public void declare(final List<String> nameList, String media, String browser) {
        media = this.canonical_(media);
        browser = this.canonical_(browser);
        for (String name : nameList) {
            this.declared_.add(new Resource(name, media, browser));
        }
    }

    @Override
    public List<String> output(final String nameList, final boolean withDependencies,
            final boolean all, final String media, final String browser) {
        if (null != nameList) {
            this.declare(nameList, null, null);
        }

        List<String> l = null;
        if (all) {
            l = this.d_.comprehend(this.getByMediaAndBrowser_(media, browser), true);
        } else if (withDependencies) {
            l = this.d_.comprehend(nameList);
        } else if (null != nameList) {
            l = new ArrayList<String>();
            String[] sa = nameList.split(SEPARATOR);
            for (String s : sa) {
                if (!l.contains(s) && !"".equals(s.trim())) {
                    l.add(s);
                }
            }
        } else {
            l = Collections.emptyList();
        }

        if (l.isEmpty()) {
            return l;
        }

        if (this.m_.isMinimizeEnabled()) {
            l = this.m_.processWithoutMinimize(l);
            l.removeAll(this.loaded_);
            this.loaded_.addAll(l);
            this.trace(l.toString());
            l = this.m_.process(l);
        } else {
            l = this.m_.process(l);
            l.removeAll(this.loaded_);
            this.loaded_.addAll(l);
        }

        return l;
    }

    @Override
    public String outputInline() {
        StringBuilder all = new StringBuilder();
        for (StringBuffer sb : this.inlines_.values()) {
            all.append(sb);
            sb.delete(0, sb.length());
        }
        return this.m_.processInline(all.toString());
    }

    public boolean isDefault(String s) {
        s = this.canonical_(s);
        return s.equalsIgnoreCase(DEFAULT);
    }

    private String canonical_(final String s) {
        if (null == s) {
            return DEFAULT;
        }
        return s.trim().replaceAll("\\s+", " ");
    }

    private Set<String> getByMediaAndBrowser_(String media, String browser) {
        Set<String> set = new HashSet<String>();
        media = this.canonical_(media);
        browser = this.canonical_(browser);
        for (Resource r : this.declared_) {
            if (r.media.equalsIgnoreCase(media) && r.browser.equalsIgnoreCase(browser)) {
                set.add(r.name);
            }
        }
        set.removeAll(this.loaded_);
        return set;
    }

    @Override
    public Set<String> getMedias(String browser) {
        Set<String> set = new HashSet<String>();
        browser = this.canonical_(browser);
        for (Resource r : this.declared_) {
            if (r.browser.equalsIgnoreCase(browser)) {
                set.add(r.media);
            }
        }
        set.remove(DEFAULT);
        return set;
    }

    @Override
    public Set<String> getBrowsers() {
        Set<String> set = new HashSet<String>();
        for (Resource r : this.declared_) {
            set.add(r.browser);
        }
        set.remove(DEFAULT);
        return set;
    }

    @Override
    public boolean hasDeclared() {
        return this.declared_.size() > 0;
    }

    private class Resource {
        String name;
        String media;
        String browser;

        public Resource(final String name, final String media, final String browser) {
            if (null == name) {
                throw new NullPointerException();
            }
            this.name = name;
            this.media = null == media ? DEFAULT : media;
            this.browser = null == browser ? DEFAULT : browser;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Resource)) {
                return false;
            }
            Resource that = (Resource) obj;
            return that.name.equals(this.name) && that.media.equals(this.media)
                    && that.browser.equals(this.browser);
        }

        @Override
        public int hashCode() {
            int ret = 17;
            ret = ret * 31 + this.name.hashCode();
            ret = ret * 31 + this.media.hashCode();
            ret = ret * 31 + this.browser.hashCode();
            return ret;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
