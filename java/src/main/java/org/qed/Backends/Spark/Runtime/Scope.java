package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.ExprId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Scope {
    private final Map<String, List<Attribute>> global = new HashMap<>();
    private final List<Map<String, List<Attribute>>> nested = new ArrayList<>();
    private final Map<String, Map<String, List<Attribute>>> inner = new HashMap<>();

    void remember(String id, Map<String, List<Attribute>> inputs) {
        inner.put(id, inputs);
    }

    Map<String, List<Attribute>> input(String id) {
        var scope = new HashMap<String, List<Attribute>>();
        if (inner.containsKey(id)) scope.putAll(inner.get(id));
        scope.put(id, get(id));
        scope.put(JOIN, get(id));
        return scope;
    }

    List<Attribute> get(String id) {
        for (int i = nested.size() - 1; i >= 0; i--) {
            var found = nested.get(i).get(id);
            if (found != null) return found;
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

    boolean visible(ExprId id) {
        if (nested.isEmpty()) return false;
        for (var columns : nested.get(nested.size() - 1).values()) for (var column : columns) if (column.exprId().equals(id)) return true;
        return false;
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
            if (found != null) return found;
        }
        return null;
    }

    static Map<String, List<Attribute>> subquery(List<Attribute> outer) {
        var inputs = new HashMap<String, List<Attribute>>();
        inputs.put(OUTER, outer);
        return inputs;
    }

    static Map<String, List<Attribute>> correlated(List<Attribute> outer, List<Attribute> local) {
        var inputs = subquery(outer);
        inputs.put(LOCAL, local);
        return inputs;
    }

    List<Attribute> join() {
        for (int i = nested.size() - 1; i >= 0; i--) {
            var found = nested.get(i).get(JOIN);
            if (found != null) return found;
        }
        return null;
    }

    static String pair(String left, String right) {
        return left + "," + right;
    }

    static Map<String, List<Attribute>> over(String id, List<Attribute> columns) {
        var inputs = new HashMap<String, List<Attribute>>();
        inputs.put(id, columns);
        inputs.put(JOIN, columns);
        return inputs;
    }

    static Map<String, List<Attribute>> over(String left, List<Attribute> leftColumns, String right, List<Attribute> rightColumns) {
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
