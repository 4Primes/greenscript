package com.greenscriptool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tree node based implementation of {@link IDependenceManager}.
 * 
 * The dependence relationships is built up during construction of the instance.
 * The input is defined in a {@link Properties}.
 * 
 * @author greenlaw110@gmail.com
 * @version 1.0.2, 2010-07-08, bug fix: refresh play plugin cause Node weight
 *          overflow;
 * @version 1.0.1, 2010-01-21, add debugString()
 * @version 1.0, 2010-10-14
 * @since 1.0
 */
public class DependenceManager implements IDependenceManager {

    private Map<String, Node> dependencies_;// = new HashMap();

    public String debugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("===============================================================").append(
                "\n DependencyManager debug information ");
        for (Node n : this.dependencies_.values()) {
            sb.append(String.format("\n\n node info: %1$s\n", n.name_)).append(n.debugString());
        }

        return sb.toString();
    }

    // public DependenceManager(){}

    /**
     * <p>
     * Create a dependency manager with a properties which contains a set of
     * dependence relationships. The format of the properties shall look like:
     * </p>
     * 
     * <code>
     * a=b,c,d
     * b=x,y
     * </code>
     * 
     * <p>
     * which means item a depends on b, c and d; b depends on x and y
     * </p>
     * 
     * <p>
     * New in 1.2d:
     * </p>
     * 
     * <code>
     * a<b<c
     * x>y>z
     * ab=xy<z,o
     * </code>
     * 
     * <p>
     * which means item a depends on b and in turn depends on c; z depends on y
     * which in turn depends on y;ab depends on both xy, z and o, while xy
     * depends on z
     * </p>
     * 
     * @param dependencies
     */
    public DependenceManager(final Properties dependencies) {
        this.dependencies_ = new HashMap<String, Node>();
        for (String s : dependencies.stringPropertyNames()) {
            String v = dependencies.getProperty(s, "");
            if (null != v && !v.trim().equals("")) {
                this.processInlineDependency(v);
                List<String> l = Arrays.asList(v.replaceAll("\\s+", "").split(SEPARATOR));
                this.createNode_(s, l);
            } else {
                this.processInlineDependency(s);
            }
        }
        for (Node n : this.dependencies_.values()) {
            n.rectify();
        }
    }

    public List<String> comprehend(final Collection<String> resourceNames) {
        return this.comprehend(resourceNames, false);
    }

    public List<String> comprehend(final Collection<String> resourceNames, final boolean withDefault) {
        if (resourceNames.isEmpty() && !withDefault) {
            return Collections.emptyList();
        }
        List<String> retList = new ArrayList<String>();
        Map<String, Node> nodes = new HashMap<String, Node>();
        List<String> undefs = new ArrayList<String>();
        for (String name : resourceNames) {
            if (null == name) {
                continue;
            }
            name = name.trim();
            if ("".equals(name)) {
                continue;
            }
            Node n = this.dependencies_.get(name);
            if (n != null) {
                nodes.put(name, n);
            } else {
                if (!undefs.contains(name)) {
                    undefs.add(name);
                }
            }
        }

        // DEFAULT nodes go first
        SortedSet<Node> defs = new TreeSet<Node>();
        if (withDefault || nodes.containsKey(DEFAULT)) {
            Node def = this.dependencies_.get(DEFAULT);
            if (null != def) {
                // remove DEFAULT from nodes as it is process right now
                nodes.remove(DEFAULT);
                defs.addAll(def.allDependOns());
                for (Node n : defs) {
                    retList.add(n.name_);
                }
            }
        }

        SortedSet<Node> all = new TreeSet<Node>();
        for (Node n : nodes.values()) {
            all.addAll(n.allDependOns());
        }
        all.removeAll(defs);
        for (Node n : all) {
            retList.add(n.name_);
        }

        retList.addAll(undefs);

        return retList;
    }

    @Override
    public List<String> comprehend(final String resourceNames) {
        return this.comprehend(resourceNames, false);
    }

    public List<String> comprehend(final String resourceNames, final boolean withDefault) {
        if (null == resourceNames) {
            return this.comprehend(new ArrayList<String>(), withDefault);
        }
        this.processInlineDependency(resourceNames);
        List<String> l = Arrays.asList(resourceNames.split(SEPARATOR));
        return this.comprehend(l, withDefault);
    }

    public List<String> comprehend() {
        return this.comprehend(DEFAULT, false);
    }

    public final void addDependency(final String dependent, final Collection<String> dependsOn) {
        this.createNode_(dependent, dependsOn);
        for (Node n : this.dependencies_.values()) {
            n.rectify();
        }
    }

    private Set<String> inlineDepDeclarations = new HashSet<String>();

    public void processInlineDependency(String dependency) {
        if (this.inlineDepDeclarations.contains(dependency)) {
            return; // already processed
        }
        this.inlineDepDeclarations.add(dependency);
        dependency = " " + dependency; // in order to match the regexp
        final Pattern p = Pattern
                .compile("(?=[\\s,;]+|(?<![\\w\\/\\-\\.:])([\\w\\/\\-\\.:]+\\s*[<>]\\s*[\\w\\/\\-\\.:]+))");
        Matcher m = p.matcher(dependency);
        boolean found = false;
        while (m.find()) {
            String g = m.group(1);
            if (null == g) {
                continue;
            }
            found = true;
            String[] relation = g.split("[<>]");
            String a = relation[0].trim();
            String b = relation[1].trim();
            if (g.indexOf('<') > -1) {
                this.createNode_(a, Arrays.asList(new String[] { b }));
            } else {
                this.createNode_(b, Arrays.asList(new String[] { a }));
            }
        }
        if (found) {
            for (Node n : this.dependencies_.values()) {
                n.rectify();
            }
        }
    }

    public static void main(final String[] args) throws IOException {
        String s = "http://ahost.com/something.js > http://zbc-1.com.au/some/path/to/x19-v1.0.js < y < z < a > b > c > d";
        String regex = "(?=[\\s,;]+|(?<![\\w\\/\\-\\.:])([\\w\\/\\-\\.:]+\\s*[<>]\\s*[\\w\\/\\-\\.:]+))";

        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(s);
        while (m.find()) {
            String d = m.group(1);
            if (d != null) {
                System.out.println(d);
            }
        }
    }

    /**
     * Create a node denoted by <code>dependent</code>. The node might not
     * necessarily be "created" if a node corresponding to the given
     * <code>dependent</code> has been created already.
     * 
     * A list of depend on resource names can be passed to build the immediate
     * dependence relationship.
     * 
     * The node created will be stored in the dependence relationship container
     * of this {@link IDependenceManager}
     * 
     * @param dependent
     * @param dependsOn
     * @return
     */
    private Node createNode_(final String dependent, final Collection<String> dependsOn) {
        Node n = this.dependencies_.get(dependent);
        if (null == n) {
            n = new Node(dependent);
            this.dependencies_.put(dependent, n);
        }

        List<String> e = Collections.emptyList();
        for (String s : dependsOn) {
            Node n0 = this.createNode_(s, e);
            n.addDependOn(n0);
        }

        return n;
    }

    /**
     * Node class abstract a dependent resource and the dependent relationship
     * between the resource and all it's depend on resources
     * 
     * @author greenlaw110@gmail.com
     */
    private static class Node implements Comparable<Node> {
        /**
         * name of the node
         */
        private final String name_;
        /**
         * a map contains all immediate depend on resource of the resource
         * denoted by this node
         * 
         * key - resource name val - resource presented by an <code>Node</code>
         */
        private final Map<String, Node> dependOns_;
        /**
         * Weight is used to help sort nodes
         * 
         * The weight of the node shall always be smaller than the weight of any
         * one of it's depend on nodes
         */
        private long weight_ = 1;

        /**
         * the smallest gap between weight of nodes
         */
        private static final int STEP_ = 10;

        /**
         * keep track whether the dependencies of this node has been updated and
         * needs rectify
         */
        private boolean dirty_ = true;

        /**
         * Construct a <code>Node</code> instance
         * 
         * @param name
         */
        private Node(final String name) {
            this.name_ = name;
            this.dependOns_ = new HashMap<String, Node>();
        }

        @Override
        public boolean equals(final Object that) {
            if (that == null) {
                return false;
            }
            if (that == this) {
                return true;
            }
            if (!(that instanceof Node)) {
                return false;
            }
            return this.name_.equals(((Node) that).name_);
        }

        @Override
        public int hashCode() {
            return this.name_.hashCode();
        }

        @Override
        public String toString() {
            return this.name_;
        }

        public String debugString() {
            String openTag = String.format("<node name='%1$s' weight='%2$s'>", this.name_,
                    this.weight_);
            String closeTag = "\n</node>";
            StringBuilder sb = new StringBuilder();
            sb.append(openTag);
            for (Node n : this.dependOns_.values()) {
                sb.append("\n\t").append(n.debugString());
            }
            sb.append(closeTag);
            return sb.toString();
        }

        public int compareTo(final Node o) {
            if (null == o) {
                return -1;
            }
            if (this.equals(o)) {
                return 0;
            }
            if (this.weight_ == o.weight_) {
                return o.name_.compareTo(this.name_);
            } else {
                long l = o.weight_ - this.weight_;
                return (l > 0) ? 1 : ((l < 0) ? -1 : 0);
            }
        }

        /**
         * Add a dependOn node
         * 
         * @param dependOn
         */
        void addDependOn(final Node dependOn) {
            // check for circular reference
            if (dependOn.dependOn_(this)) {
                throw new CircularDependenceException(this.name_, dependOn.name_);
            }
            this.dependOns_.put(dependOn.name_, dependOn);
            this.dirty_ = true;
        }

        /**
         * Return all depend on nodes of this node, including indirectly depend
         * on nodes, i.e. the nodes depended on by the depend on node(s) of this
         * node
         * 
         * the return set also include this node itself as this node depend on
         * it self.
         * 
         * @return
         */
        Set<Node> allDependOns() {
            Set<Node> all = new HashSet<Node>();
            if (this.dirty_) {
                if (!this.dependOns_.isEmpty()) {
                    for (Node n0 : this.dependOns_.values()) {
                        all.addAll(n0.allDependOns());
                        all.add(n0);
                    }
                }
            } else {
                all.addAll(this.dependOns_.values());
            }
            all.add(this);
            return all;
        }

        /**
         * Flatten the dependence relationship and then recalculate weight of
         * depend on nodes
         */
        void rectify() {
            this.flatten_();
            this.updateDependOnWeights_();
            this.dirty_ = false;
        }

        /**
         * Turn indirect dependencies into direct dependencies
         */
        private void flatten_() {
            for (Node n0 : this.dependOns_.values()) {
                n0.flatten_();
            }
            for (Node n0 : new HashSet<Node>(this.dependOns_.values())) {
                this.dependOns_.putAll(n0.dependOns_);
            }
        }

        /**
         * update weights of depend on nodes based on the weight of this node
         */
        void updateDependOnWeights_() {
            this.incWeightOn_(null);
        }

        private void incWeightOn_(final Node node) {
            if (null != node && this.weight_ <= node.weight_) {
                this.weight_ = node.weight_ + STEP_;
            }
            for (Node dependOn : this.dependOns_.values()) {
                dependOn.incWeightOn_(this);
            }
        }

        /**
         * Test whether a given node is depend on node of this node
         * 
         * @param node
         * @return
         */
        private boolean dependOn_(final Node node) {
            if (this.dependOns_.containsKey(node.name_)) {
                return true;
            }
            if (this.dirty_) {
                for (Node n0 : this.dependOns_.values()) {
                    if (n0.dependOn_(node)) {
                        return true;
                    }
                }
            }
            return false;
        }

    }
}
