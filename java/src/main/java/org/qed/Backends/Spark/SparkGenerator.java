package org.qed.Backends.Spark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kala.collection.Seq;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SparkGenerator {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CASE_OPERATOR = "CASE";

    private final Map<String, String> ids = new LinkedHashMap<>();
    private final Map<RelRN, ObjectNode> built = new IdentityHashMap<>();
    private final Map<String, List<TypeRef>> typeRefs = new LinkedHashMap<>();

    private record TypeRef(String of, int ordinal) { }

    public String generate(RRule rule) {
        return new SparkGenerator().rule(rule);
    }

    private String rule(RRule rule) {
        var node = MAPPER.createObjectNode();
        node.put("name", rule.name());
        collectTypeRefs(rule.before());
        node.set("from", rel(rule.before()));
        node.set("to", rel(rule.after()));
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void collectTypeRefs(RelRN rel) {
        switch (rel) {
            case RelRN.Scan scan -> typeRefs.computeIfAbsent(scan.ty().toString(), k -> new ArrayList<>())
                    .add(new TypeRef(id(scan), 0));
            case RelRN.ScanMany scan -> {
                for (int i = 0; i < scan.tys().size(); i++) {
                    typeRefs.computeIfAbsent(scan.tys().get(i).toString(), k -> new ArrayList<>())
                            .add(new TypeRef(id(scan), i));
                }
            }
            case RelRN.Filter filter -> collectTypeRefs(filter.source());
            case RelRN.Project project -> collectTypeRefs(project.source());
            case RelRN.ProjectMany project -> collectTypeRefs(project.source());
            case RelRN.Aggregate aggregate -> collectTypeRefs(aggregate.source());
            case RelRN.Join join -> { collectTypeRefs(join.left()); collectTypeRefs(join.right()); }
            case RelRN.JoinWithSeparateConds join -> { collectTypeRefs(join.left()); collectTypeRefs(join.right()); }
            case RelRN.JoinWithPushedConds join -> { collectTypeRefs(join.left()); collectTypeRefs(join.right()); }
            case RelRN.Union set -> set.sources().forEach(this::collectTypeRefs);
            case RelRN.Intersect set -> set.sources().forEach(this::collectTypeRefs);
            case RelRN.Minus set -> set.sources().forEach(this::collectTypeRefs);
            case RelRN.Empty empty -> collectTypeRefs(empty.sourceType());
            default -> { }
        }
    }

    private String id(RelRN rel) {
        return rel(rel).path("id").asText();
    }

    private ObjectNode rel(RelRN rel) {
        var cached = built.get(rel);
        if (cached != null) {
            return cached;
        }
        var node = build(rel);
        node.put("id", ids.computeIfAbsent(node.toString(), key -> "r" + ids.size()));
        built.put(rel, node);
        return node;
    }

    private ObjectNode build(RelRN rel) {
        var node = MAPPER.createObjectNode();
        switch (rel) {
            case RelRN.Scan scan -> {
                node.put("op", "scan");
                node.put("name", scan.name());
                node.put("arity", 1);
                if (!scan.ty().isNullable()) {
                    node.putArray("nonnull").add(0);
                }
                if (scan.unique()) {
                    node.put("unique", true);
                }
            }
            case RelRN.ScanMany scan -> {
                node.put("op", "scan");
                node.put("name", scan.name());
                node.put("arity", scan.tys().size());
                var nonNull = MAPPER.createArrayNode();
                for (int i = 0; i < scan.tys().size(); i++) {
                    if (!scan.tys().get(i).isNullable()) {
                        nonNull.add(i);
                    }
                }
                if (!nonNull.isEmpty()) {
                    node.set("nonnull", nonNull);
                }
            }
            case RelRN.Filter filter -> {
                node.put("op", "filter");
                node.set("input", rel(filter.source()));
                node.set("cond", rex(filter.cond()));
            }
            case RelRN.Project project -> {
                node.put("op", "project");
                node.set("input", rel(project.source()));
                node.set("exprs", MAPPER.createArrayNode().add(rex(project.map())));
            }
            case RelRN.ProjectMany project -> {
                node.put("op", "project");
                node.set("input", rel(project.source()));
                var exprs = MAPPER.createArrayNode();
                project.exprs().forEach(e -> exprs.add(rex(e)));
                node.set("exprs", exprs);
            }
            case RelRN.Correlate correlate -> {
                node.put("op", "correlate");
                node.put("type", joinKind(correlate.ty()));
                node.set("left", rel(correlate.left()));
                node.set("right", rel(correlate.right()));
                var args = MAPPER.createArrayNode();
                for (int i = 0; i < correlate.left().semantics().getRowType().getFieldCount(); i++) {
                    args.addObject().put("op", "outer").put("ordinal", i);
                }
                for (int i = 0; i < correlate.right().semantics().getRowType().getFieldCount(); i++) {
                    args.addObject().put("op", "local").put("ordinal", i);
                }
                var cond = MAPPER.createObjectNode();
                if (correlate.cond() instanceof RuleBuilder.QedFunction) {
                    cond.put("op", "pred");
                    cond.put("name", correlate.cond().getName());
                } else {
                    cond.put("op", "scalar");
                    cond.put("operator", operator(correlate.cond().getKind(), correlate.cond().getName()));
                }
                cond.set("args", args);
                node.set("cond", cond);
            }
            case RelRN.Join join -> join(node, join.ty(), join.cond(), join.left(), join.right());
            case RelRN.JoinWithSeparateConds join -> join(node, join.ty(), join.cond(), join.left(), join.right());
            case RelRN.JoinWithPushedConds join -> join(node, join.ty(), join.cond(), join.left(), join.right());
            case RelRN.Union union -> set(node, "union", union.all(), union.sources());
            case RelRN.Intersect intersect -> set(node, "intersect", intersect.all(), intersect.sources());
            case RelRN.Minus minus -> set(node, "except", minus.all(), minus.sources());
            case RelRN.Empty empty -> {
                node.put("op", "empty");
                node.put("of", id(empty.sourceType()));
                node.put("arity", empty.sourceType().semantics().getRowType().getFieldCount());
            }
            case RelRN.Aggregate aggregate -> {
                node.put("op", "aggregate");
                node.set("input", rel(aggregate.source()));
                var groups = MAPPER.createArrayNode();
                aggregate.groupSet().forEach(g -> groups.add(rex(g)));
                node.set("groups", groups);
                var aggs = MAPPER.createArrayNode();
                aggregate.aggCalls().forEach(a -> aggs.add(aggCall(a)));
                node.set("aggs", aggs);
            }
            default -> lift(node, rel);
        }
        return node;
    }

    private void lift(ObjectNode node, RelRN rel) {
        var semantics = rel.semantics();
        if (semantics instanceof org.apache.calcite.rel.core.Values values) {
            node.put("op", "values");
            var rows = node.putArray("rows");
            for (var tuple : values.getTuples()) {
                var row = rows.addArray();
                for (var literal : tuple) {
                    row.add(literal(literal));
                }
            }
            if (rows.isEmpty()) {
                throw new UnsupportedOperationException("Unsupported empty concrete Values");
            }
        } else if (semantics instanceof org.apache.calcite.rel.core.TableScan scan) {
            node.put("op", "scan");
            node.put("name", scan.getTable().getQualifiedName().get(0));
            var fields = scan.getRowType().getFieldList();
            node.put("arity", fields.size());
            var types = node.putArray("types");
            var nonNull = node.putArray("nonnull");
            for (int i = 0; i < fields.size(); i++) {
                if (fields.get(i).getType().getSqlTypeName() != SqlTypeName.INTEGER) {
                    throw new UnsupportedOperationException("Unsupported column type: " + fields.get(i).getType());
                }
                types.add("int");
                if (!fields.get(i).getType().isNullable()) {
                    nonNull.add(i);
                }
            }
            if (nonNull.isEmpty()) {
                node.remove("nonnull");
            }
        } else if (semantics instanceof org.apache.calcite.rel.RelNode lifted) {
            var previous = owner;
            owner = rel;
            try {
                node.setAll(lift(lifted));
            } finally {
                owner = previous;
            }
        } else {
            throw new UnsupportedOperationException("Unsupported relational operator: " + constructName(rel.getClass()));
        }
    }

    private RelRN owner;

    private ObjectNode lift(org.apache.calcite.rel.RelNode node) {
        try {
            for (var component : owner.getClass().getRecordComponents()) {
                component.getAccessor().setAccessible(true);
                if (component.getAccessor().invoke(owner) instanceof RelRN rel
                        && rel.semantics().explain().equals(node.explain())) {
                    return rel(rel);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException("Unsupported relational operator: " + constructName(owner.getClass()));
        }
        var out = MAPPER.createObjectNode();
        if (node instanceof org.apache.calcite.rel.core.Filter filter) {
            out.put("op", "filter");
            out.set("input", lift(filter.getInput()));
            out.set("cond", lift(filter.getCondition()));
        } else if (node instanceof org.apache.calcite.rel.core.Join join) {
            out.put("op", "join");
            out.put("type", joinKind(join.getJoinType()));
            out.set("left", lift(join.getLeft()));
            out.set("right", lift(join.getRight()));
            out.set("cond", lift(join.getCondition()));
        } else {
            throw new UnsupportedOperationException("Unsupported relational operator: " + node.getRelTypeName());
        }
        out.put("id", ids.computeIfAbsent(out.toString(), key -> "r" + ids.size()));
        return out;
    }

    private ObjectNode lift(org.apache.calcite.rex.RexNode rex) {
        var node = MAPPER.createObjectNode();
        if (rex instanceof RexInputRef ref) {
            node.put("op", "input");
            node.put("ordinal", ref.getIndex());
        } else if (rex instanceof RexLiteral literal) {
            node.setAll(literal(literal));
        } else if (rex instanceof org.apache.calcite.rex.RexFieldAccess access
                && access.getReferenceExpr() instanceof org.apache.calcite.rex.RexCorrelVariable) {
            node.put("op", "outer");
            node.put("ordinal", access.getField().getIndex());
        } else if (rex instanceof org.apache.calcite.rex.RexSubQuery sub) {
            switch (sub.getKind()) {
                case EXISTS -> node.put("op", "exists");
                case IN -> {
                    node.put("op", "in");
                    var values = node.putArray("values");
                    sub.getOperands().forEach(o -> values.add(lift(o)));
                }
                default -> throw new UnsupportedOperationException("Unsupported subquery: " + sub.getKind());
            }
            node.set("input", lift(sub.rel));
        } else if (rex instanceof org.apache.calcite.rex.RexCall call) {
            var args = MAPPER.createArrayNode();
            call.getOperands().forEach(o -> args.add(lift(o)));
            switch (call.getKind()) {
                case AND -> node.put("op", "and").set("args", args);
                case OR -> node.put("op", "or").set("args", args);
                case NOT -> node.put("op", "not").set("arg", args.get(0));
                default -> {
                    node.put("op", "scalar");
                    node.put("operator", operator(call.getKind(), call.getOperator().getName()));
                    node.set("args", args);
                }
            }
        } else {
            throw new UnsupportedOperationException("Unsupported expression construct: " + rex.getKind());
        }
        return node;
    }

    private void join(ObjectNode node, RelRN.Join.JoinType type, RexRN cond, RelRN left, RelRN right) {
        if (!(type instanceof RelRN.Join.JoinType.ConcreteJoinType concrete)) {
            throw new UnsupportedOperationException("Unsupported join type family: " + type);
        }
        node.put("op", "join");
        node.put("type", joinKind(concrete.type()));
        node.set("left", rel(left));
        node.set("right", rel(right));
        node.set("cond", rex(cond));
    }

    private static String joinKind(org.apache.calcite.rel.core.JoinRelType type) {
        return switch (type) {
            case INNER -> "inner";
            case LEFT -> "left";
            case RIGHT -> "right";
            case FULL -> "full";
            case SEMI -> "semi";
            case ANTI -> "anti";
            default -> throw new UnsupportedOperationException("Unsupported join type: " + type);
        };
    }

    private void set(ObjectNode node, String kind, boolean all, Seq<RelRN> sources) {
        if (sources.isEmpty()) {
            throw new UnsupportedOperationException("Unsupported set operation arity: 0");
        }
        if (sources.size() == 1 && !kind.equals("union")) {
            throw new UnsupportedOperationException("Unsupported single-input set operation");
        }
        node.put("op", "set");
        node.put("kind", kind);
        node.put("all", all);
        var inputs = MAPPER.createArrayNode();
        if (kind.equals("union")) {
            sources.forEach(s -> inputs.add(rel(s)));
        } else {
            RelRN left = sources.get(0);
            for (int i = 1; i < sources.size() - 1; i++) {
                var nested = kind.equals("intersect") ? new RelRN.Intersect(all, Seq.of(left, sources.get(i)))
                        : new RelRN.Minus(all, Seq.of(left, sources.get(i)));
                left = nested;
            }
            inputs.add(rel(left));
            inputs.add(rel(sources.get(sources.size() - 1)));
        }
        node.set("inputs", inputs);
    }

    private ObjectNode aggCall(RelRN.AggCall agg) {
        if (agg.operator() != null && !(agg.operator() instanceof RuleBuilder.QedAggregateFunction)) {
            throw new UnsupportedOperationException("Unsupported concrete aggregate operator: "
                    + agg.operator().getName());
        }
        var node = MAPPER.createObjectNode();
        node.put("op", "agg");
        node.put("name", agg.name());
        node.put("distinct", agg.distinct());
        node.set("args", arguments(agg.operands()));
        return node;
    }

    private ObjectNode rex(RexRN rex) {
        var node = MAPPER.createObjectNode();
        switch (rex) {
            case RexRN.Pred pred -> {
                if (pred.operator() instanceof RuleBuilder.QedFunction) {
                    symbol(node, "pred", pred.operator().getName(), pred.sources());
                } else {
                    call(node, pred.operator().getKind(), pred.operator().getName(), pred.sources());
                }
            }
            case RexRN.Proj proj -> {
                if (proj.operator() instanceof RuleBuilder.QedFunction && proj.operator().getName().equals(CASE_OPERATOR)
                        && proj.sources().size() == 3) {
                    node.put("op", "scalar");
                    node.put("operator", "if");
                    node.set("args", listOf(proj.sources()));
                } else if (proj.operator() instanceof RuleBuilder.QedFunction) {
                    symbol(node, "proj", proj.operator().getName(), proj.sources());
                } else {
                    call(node, proj.operator().getKind(), proj.operator().getName(), proj.sources());
                }
            }
            case RexRN.GroupBy group -> {
                if (!(group.operator() instanceof RuleBuilder.QedFunction)) {
                    throw new UnsupportedOperationException("Unsupported concrete grouping operator: "
                            + group.operator().getName());
                }
                symbol(node, "group_key", group.operator().getName(), group.sources());
            }
            case RexRN.And and -> list(node, "and", and.sources());
            case RexRN.Or or -> list(node, "or", or.sources());
            case RexRN.Not not -> {
                node.put("op", "not");
                node.set("arg", rex(not.source()));
            }
            case RexRN.True ignored -> node.put("op", "true");
            case RexRN.False ignored -> node.put("op", "false");
            case RexRN.Field field -> {
                node.put("op", "field");
                node.put("ordinal", field.ordinal());
                node.put("of", id(field.source()));
            }
            case RexRN.JoinField field -> {
                node.put("op", "joinfield");
                node.put("ordinal", field.ordinal());
                node.put("left", id(field.left()));
                node.put("right", id(field.right()));
            }
            default -> {
                var semantics = rex.semantics();
                var subquery = subquery(rex, semantics);
                if (subquery != null) {
                    node.setAll(subquery);
                } else if (semantics instanceof RexLiteral literal) {
                    node.setAll(literal(literal));
                } else if (semantics instanceof RexInputRef ref) {
                    node.put("op", "input");
                    node.put("ordinal", ref.getIndex());
                } else {
                    throw new UnsupportedOperationException("Unsupported expression construct: "
                            + constructName(rex.getClass()));
                }
            }
        }
        return node;
    }

    private ObjectNode subquery(RexRN rex, org.apache.calcite.rex.RexNode semantics) {
        var and = semantics instanceof org.apache.calcite.rex.RexCall call && call.getKind() == SqlKind.AND;
        var last = and ? ((org.apache.calcite.rex.RexCall) semantics).getOperands()
                .get(((org.apache.calcite.rex.RexCall) semantics).getOperands().size() - 1) : semantics;
        if (!(last instanceof org.apache.calcite.rex.RexSubQuery sub) || sub.getKind() != SqlKind.EXISTS
                || rex.getClass().getRecordComponents() == null) {
            return null;
        }
        RelRN body = null;
        var guards = new ArrayList<RexRN>();
        try {
            for (var component : rex.getClass().getRecordComponents()) {
                component.getAccessor().setAccessible(true);
                var value = component.getAccessor().invoke(rex);
                if (value instanceof RelRN rel && body == null) {
                    body = rel;
                } else if (value instanceof RexRN guard) {
                    guards.add(guard);
                } else {
                    return null;
                }
            }
        } catch (ReflectiveOperationException e) {
            return null;
        }
        if (body == null) {
            return null;
        }
        var exists = MAPPER.createObjectNode();
        exists.put("op", "exists");
        exists.set("input", rel(body));
        var conjuncts = new ArrayList<ObjectNode>();
        for (var guard : guards) {
            if (!guard.semantics().isAlwaysTrue()) {
                conjuncts.add(rex(guard));
            }
        }
        if (conjuncts.size() != (and ? ((org.apache.calcite.rex.RexCall) semantics).getOperands().size() - 1 : 0)) {
            return null;
        }
        if (conjuncts.isEmpty()) {
            return exists;
        }
        var all = MAPPER.createObjectNode();
        all.put("op", "and");
        var args = all.putArray("args");
        conjuncts.forEach(args::add);
        args.add(exists);
        return all;
    }

    private ObjectNode literal(RexLiteral literal) {
        var node = MAPPER.createObjectNode();
        var type = literal.getType();
        if (literal.isNull()) {
            node.put("op", "null");
            if (type.getSqlTypeName() == SqlTypeName.BOOLEAN) {
                node.put("type", "bool");
            } else if (type.getSqlTypeName() == SqlTypeName.INTEGER) {
                node.put("type", "int");
            } else if (type instanceof RelType.VarType) {
                var refs = typeRefs.get(type.toString());
                if (refs == null || refs.isEmpty()) {
                    throw new UnsupportedOperationException("Unsupported unbound NULL type variable: " + type);
                }
                node.put("type", "same_as");
                var out = node.putArray("refs");
                for (var ref : refs) {
                    out.addObject().put("of", ref.of()).put("ordinal", ref.ordinal());
                }
            } else {
                throw new UnsupportedOperationException("Unsupported NULL literal type: " + type);
            }
        } else if (type.getSqlTypeName() == SqlTypeName.INTEGER) {
            node.put("op", "int");
            node.put("value", literal.getValueAs(Integer.class));
        } else if (type.getSqlTypeName() == SqlTypeName.BIGINT) {
            node.put("op", "int");
            node.put("value", literal.getValueAs(Long.class));
            node.put("type", "long");
        } else if (type.getSqlTypeName() == SqlTypeName.BOOLEAN) {
            node.put("op", Boolean.TRUE.equals(literal.getValueAs(Boolean.class)) ? "true" : "false");
        } else {
            throw new UnsupportedOperationException("Unsupported literal: " + literal);
        }
        return node;
    }

    private void call(ObjectNode node, SqlKind kind, String name, Seq<RexRN> sources) {
        node.put("op", "scalar");
        node.put("operator", operator(kind, name));
        node.set("args", listOf(sources));
    }

    private static String operator(SqlKind kind, String name) {
        return switch (kind) {
            case EQUALS -> "=";
            case NOT_EQUALS -> "<>";
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "<=";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> ">=";
            case IS_NOT_DISTINCT_FROM -> "<=>";
            case IS_NULL -> "IS NULL";
            case IS_NOT_NULL -> "IS NOT NULL";
            case IS_TRUE -> "IS TRUE";
            case IS_NOT_TRUE -> "IS NOT TRUE";
            case IS_FALSE -> "IS FALSE";
            case IS_NOT_FALSE -> "IS NOT FALSE";
            case PLUS -> "+";
            case MINUS -> "-";
            case MINUS_PREFIX -> "neg";
            case TIMES -> "*";
            default -> throw new UnsupportedOperationException("Unsupported concrete operator: " + name);
        };
    }

    private void list(ObjectNode node, String op, Seq<RexRN> sources) {
        node.put("op", op);
        node.set("args", listOf(sources));
    }

    private ArrayNode listOf(Seq<RexRN> sources) {
        var args = MAPPER.createArrayNode();
        sources.forEach(s -> args.add(rex(s)));
        return args;
    }

    private void symbol(ObjectNode node, String op, String name, Seq<RexRN> sources) {
        node.put("op", op);
        node.put("name", name);
        node.set("args", arguments(sources));
    }

    private ArrayNode arguments(Seq<RexRN> sources) {
        var args = MAPPER.createArrayNode();
        var rels = wholeRows(sources);
        if (rels != null) {
            var row = args.addObject();
            row.put("op", "row");
            var of = row.putArray("rels");
            rels.forEach(r -> of.add(id(r)));
            return args;
        }
        sources.forEach(s -> args.add(rex(s)));
        for (var arg : args) {
            if (Set.of("proj", "pred", "group_key").contains(arg.path("op").asText())) {
                for (var inner : arg.path("args")) {
                    if (Set.of("proj", "pred", "group_key").contains(inner.path("op").asText())) {
                        throw new UnsupportedOperationException("Unsupported symbol applied to symbols two levels deep");
                    }
                }
            }
        }
        return args;
    }

    private static List<RelRN> wholeRows(Seq<RexRN> sources) {
        if (sources.isEmpty()) {
            return null;
        }
        if (sources.allMatch(s -> s instanceof RexRN.Field)) {
            var source = ((RexRN.Field) sources.get(0)).source();
            var count = source.semantics().getRowType().getFieldCount();
            for (var i = 0; i < sources.size(); i++) {
                var field = (RexRN.Field) sources.get(i);
                if (!field.source().equals(source) || field.ordinal() != i) {
                    return null;
                }
            }
            return sources.size() == count ? List.of(source) : null;
        }
        if (sources.allMatch(s -> s instanceof RexRN.JoinField)) {
            var first = (RexRN.JoinField) sources.get(0);
            var total = first.left().semantics().getRowType().getFieldCount()
                    + first.right().semantics().getRowType().getFieldCount();
            for (var i = 0; i < sources.size(); i++) {
                var field = (RexRN.JoinField) sources.get(i);
                if (!field.left().equals(first.left()) || !field.right().equals(first.right())
                        || field.ordinal() != i) {
                    return null;
                }
            }
            return sources.size() == total ? List.of(first.left(), first.right()) : null;
        }
        return null;
    }

    private static String constructName(Class<?> type) {
        if (type.isSynthetic()) {
            return "Java lambda";
        }
        if (type.isAnonymousClass()) {
            return "anonymous " + type.getInterfaces()[0].getSimpleName();
        }
        return type.getSimpleName();
    }
}
