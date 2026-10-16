package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The output columns of the relations of a pattern. An expression refers to the relations it is evaluated over, so while
 * the expressions of a node are handled, the inputs of that node take precedence over the last relation with the
 * same name, which matters when a relation occurs more than once.
 */
final class Scope {
    private final Map<String, List<Attribute>> global = new HashMap<>();
    private final List<Map<String, List<Attribute>>> nested = new ArrayList<>();
    private final Map<String, Map<String, List<Attribute>>> inner = new HashMap<>();

    /** Remembers the inputs of a node, which expressions over the node may still refer to (the sides of a join). */
    void remember(String id, Map<String, List<Attribute>> inputs) {
        inner.put(id, inputs);
    }

    /** The scope of an expression over the node: its columns, and the inputs it remembers. */
    Map<String, List<Attribute>> input(String id) {
        var scope = new HashMap<String, List<Attribute>>();
        if (inner.containsKey(id)) {
            scope.putAll(inner.get(id));
        }
        scope.put(id, get(id));
        scope.put(JOIN, get(id));
        return scope;
    }

    List<Attribute> get(String id) {
        for (int i = nested.size() - 1; i >= 0; i--) {
            var found = nested.get(i).get(id);
            if (found != null) {
                return found;
            }
        }
        return global.get(id);
    }

    void put(String id, List<Attribute> columns) {
        global.put(id, columns);
    }

    boolean containsKey(String id) {
        return global.containsKey(id);
    }

    void push(Map<String, List<Attribute>> inputs) {
        nested.add(inputs);
    }

    void pop() {
        nested.remove(nested.size() - 1);
    }

    static final String JOIN = "\0join";
    static final String OUTER = "\0outer";
    static final String LOCAL = "\0local";

    List<Attribute> outer() {
        return find(OUTER);
    }

    List<Attribute> local() {
        return find(LOCAL);
    }

    private List<Attribute> find(String key) {
        for (int i = nested.size() - 1; i >= 0; i--) {
            var found = nested.get(i).get(key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The scope inside a subquery: the columns of the enclosing row, which the subquery refers to as outer. */
    static Map<String, List<Attribute>> subquery(List<Attribute> outer) {
        var inputs = new HashMap<String, List<Attribute>>();
        inputs.put(OUTER, outer);
        return inputs;
    }

    /** The scope of a correlation condition: the columns of the left (outer) side and of the right (local) side. */
    static Map<String, List<Attribute>> correlated(List<Attribute> outer, List<Attribute> local) {
        var inputs = subquery(outer);
        inputs.put(LOCAL, local);
        return inputs;
    }

    List<Attribute> join() {
        for (int i = nested.size() - 1; i >= 0; i--) {
            var found = nested.get(i).get(JOIN);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static String pair(String left, String right) {
        return left + "," + right;
    }

    /** The scope of an expression over one input. */
    static Map<String, List<Attribute>> over(String id, List<Attribute> columns) {
        var inputs = new HashMap<String, List<Attribute>>();
        inputs.put(id, columns);
        inputs.put(JOIN, columns);
        return inputs;
    }

    /** The scope of an expression over two inputs, whose columns are those of the left followed by the right. */
    static Map<String, List<Attribute>> over(String left, List<Attribute> leftColumns, String right,
                                             List<Attribute> rightColumns) {
        var inputs = new HashMap<String, List<Attribute>>();
        inputs.put(left, leftColumns);
        inputs.put(right, rightColumns);
        var both = new ArrayList<>(leftColumns);
        both.addAll(rightColumns);
        inputs.put(pair(left, right), both);
        inputs.put(JOIN, both);
        return inputs;
    }
}
