package org.qed.spark;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.ExprId;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.GreaterThanOrEqual;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.LessThanOrEqual;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Multiply$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.expressions.Subtract$;
import org.apache.spark.sql.catalyst.expressions.UnaryMinus$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateFunction;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.plans.FullOuter$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.RightOuter$;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.InSubquery;
import org.apache.spark.sql.catalyst.expressions.LateralSubquery;
import org.apache.spark.sql.catalyst.expressions.ListQuery;
import org.apache.spark.sql.catalyst.expressions.OuterReference;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LateralJoin;
import org.apache.spark.sql.catalyst.plans.logical.Except;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.IntegerType$;
import scala.Option;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Match {
    static final class Abort extends RuntimeException {
        Abort() {
            super("rewrite aborted");
        }
    }

    record Binding(List<Attribute> params, Expression body, Alias alias) { }

    private record Pending(JsonNode pattern, Expression expr, Map<String, Binding> bindings, Alias alias) { }

    private final List<Pending> pending = new ArrayList<>();

    private static final boolean DEBUG = System.getenv("RULESCRIPT_DEBUG") != null;

    private final Scope cols = new Scope();
    private final Map<String, LogicalPlan> plans = new HashMap<>();
    private final Set<ExprId> usedAliases = new HashSet<>();
    private final Map<String, List<LogicalPlan>> scans = new HashMap<>();
    private final Map<String, Integer> scanUses = new HashMap<>();
    private final Map<String, Binding> preds = new HashMap<>();
    private final Map<String, Binding> projs = new HashMap<>();
    private final Map<String, Binding> groupKeys = new HashMap<>();
    private Map<ExprId, Attribute> outerIds = new HashMap<>();
    private List<Attribute> outerUsed = new ArrayList<>();
    private final Map<String, Binding> aggs = new HashMap<>();

    List<Attribute> cols(String id) {
        var found = cols.get(id);
        if (found == null) {
            throw new Abort();
        }
        return found;
    }

    boolean rel(JsonNode p, LogicalPlan e) {
        var id = p.path("id").asText();
        var ok = switch (p.path("op").asText()) {
            case "scan" -> scan(p, e);
            case "filter" -> filter(p, e);
            case "project" -> project(p, e);
            case "join" -> join(p, e);
            case "correlate" -> correlate(p, e);
            case "aggregate" -> aggregate(p, e);
            case "set" -> set(p, e);
            case "empty" -> empty(p, e);
            case "values" -> values(p, e);
            default -> false;
        };
        if (ok) {
            plans.put(id, e);
        } else if (DEBUG) {
            System.out.println("MISMATCH " + p.path("op").asText() + " " + id + " against " + e.nodeName());
        }
        return ok;
    }

    private boolean scan(JsonNode p, LogicalPlan e) {
        var output = Catalyst.list(e.output());
        if (output.size() != p.path("arity").asInt()) {
            return false;
        }
        for (var ordinal : p.path("nonnull")) {
            if (output.get(ordinal.asInt()).nullable()) {
                return false;
            }
        }
        for (int i = 0; i < p.path("types").size(); i++) {
            if (!output.get(i).dataType().equals(IntegerType$.MODULE$)) {
                return false;
            }
        }
        if (p.path("unique").asBoolean() && !isUnique(e, output.get(0))) {
            return false;
        }
        var name = p.path("name").asText();
        var seen = scans.computeIfAbsent(name, k -> new ArrayList<>());
        if (!seen.isEmpty() && !seen.get(0).sameResult(e)) {
            return false;
        }
        seen.add(e);
        cols.put(p.path("id").asText(), output);
        return true;
    }

    private static boolean isUnique(LogicalPlan plan, Attribute column) {
        if (plan instanceof LocalRelation local) {
            var output = Catalyst.list(local.output());
            int ordinal = -1;
            for (int i = 0; i < output.size(); i++) {
                if (Catalyst.sameAttribute(output.get(i), column)) {
                    ordinal = i;
                }
            }
            if (ordinal >= 0 && column.dataType().equals(IntegerType$.MODULE$)) {
                var seen = new HashSet<Object>();
                for (var row : Catalyst.list(local.data())) {
                    if (!seen.add(row.isNullAt(ordinal) ? "null" : (Object) row.getInt(ordinal))) {
                        return false;
                    }
                }
                return true;
            }
        }
        for (var key : Catalyst.list(plan.distinctKeys().toSeq())) {
            var attrs = Catalyst.<Expression>list(key.toSeq());
            if (attrs.stream().allMatch(a -> Catalyst.sameAttribute(a, column))) {
                return true;
            }
        }
        return false;
    }

    private boolean filter(JsonNode p, LogicalPlan e) {
        if (!(e instanceof Filter f) || !rel(p.path("input"), f.child())) {
            return false;
        }
        var inputId = p.path("input").path("id").asText();
        cols.put(p.path("id").asText(), cols(inputId));
        cols.remember(p.path("id").asText(), cols.input(inputId));
        cols.push(cols.input(inputId));
        try {
            return condition(p.path("cond"), Catalyst.conjuncts(Catalyst.stripOuter(f.condition())));
        } finally {
            cols.pop();
        }
    }

    private boolean project(JsonNode p, LogicalPlan e) {
        if (!(e instanceof Project proj) || !rel(p.path("input"), proj.child())) {
            return false;
        }
        var items = Catalyst.list(proj.projectList());
        var exprs = p.path("exprs");
        if (items.size() != exprs.size()) {
            return false;
        }
        var out = new ArrayList<Attribute>();
        var inputId = p.path("input").path("id").asText();
        cols.push(cols.input(inputId));
        try {
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            var pattern = exprs.get(i);
            Expression value = (Expression) item;
            Alias alias = null;
            if (item instanceof Alias a) {
                value = a.child();
                alias = a;
            }
            if (isColumn(pattern)) {
                if (!(item instanceof Attribute) || !Catalyst.sameAttribute((Expression) item, one(pattern))) {
                    return false;
                }
            } else if (pattern.path("op").asText().equals("proj")) {
                if (!symbol(pattern, value, projs, alias)) {
                    return false;
                }
            } else if (!scalar(pattern, value)) {
                return false;
            }
            out.add(item.toAttribute());
        }
        } finally {
            cols.pop();
        }
        cols.put(p.path("id").asText(), out);
        return true;
    }

    private boolean join(JsonNode p, LogicalPlan e) {
        if (!(e instanceof Join j) || !joinType(p.path("type").asText()).equals(j.joinType())
                || !rel(p.path("left"), j.left())) {
            return false;
        }
        var leftColumns = cols(p.path("left").path("id").asText());
        if (!rel(p.path("right"), j.right())) {
            return false;
        }
        var rightColumns = cols(p.path("right").path("id").asText());
        cols.put(p.path("id").asText(), Catalyst.list(j.output()));
        var cond = j.condition();
        var parts = cond.isEmpty() ? new ArrayList<Expression>() : Catalyst.conjuncts(Catalyst.stripOuter(cond.get()));
        var leftId = p.path("left").path("id").asText();
        var rightId = p.path("right").path("id").asText();
        cols.remember(p.path("id").asText(), Scope.over(leftId, leftColumns, rightId, rightColumns));
        cols.push(Scope.over(leftId, leftColumns, rightId, rightColumns));
        try {
            return condition(p.path("cond"), parts);
        } finally {
            cols.pop();
        }
    }

    private boolean correlate(JsonNode p, LogicalPlan e) {
        var type = p.path("type").asText();
        LogicalPlan left;
        LogicalPlan right;
        Expression cond;
        if (type.equals("inner") || type.equals("left")) {
            if (!(e instanceof LateralJoin lateral) || !joinType(type).equals(lateral.joinType())
                    || lateral.condition().isDefined() || !(lateral.right().plan() instanceof Filter filter)) {
                return false;
            }
            left = lateral.left();
            right = filter.child();
            cond = filter.condition();
        } else {
            if (!(e instanceof Join join) || !joinType(type).equals(join.joinType()) || join.condition().isEmpty()) {
                return false;
            }
            left = join.left();
            right = join.right();
            cond = join.condition().get();
        }
        if (!rel(p.path("left"), left) || !rel(p.path("right"), right)) {
            return false;
        }
        var outer = cols(p.path("left").path("id").asText());
        var local = cols(p.path("right").path("id").asText());
        cols.put(p.path("id").asText(), Catalyst.list(e.output()));
        cols.push(Scope.correlated(outer, local));
        try {
            return condition(p.path("cond"), Catalyst.conjuncts(Catalyst.stripOuter(cond)));
        } finally {
            cols.pop();
        }
    }

    private static LogicalPlan withJoinCondition(LogicalPlan plan, scala.collection.immutable.Seq<Expression> joinCond) {
        var conditions = Catalyst.<Expression>list(joinCond);
        return conditions.isEmpty() ? plan : new Filter(Catalyst.conjunction(conditions), plan);
    }

    private boolean subquery(JsonNode input, LogicalPlan plan) {
        cols.push(Scope.subquery(cols.join()));
        try {
            return rel(input, plan);
        } finally {
            cols.pop();
        }
    }

    private boolean condition(JsonNode p, List<Expression> parts) {
        switch (p.path("op").asText()) {
            case "true" -> {
                return parts.isEmpty() || (parts.size() == 1 && Catalyst.isTrue(parts.get(0)));
            }
            case "and" -> {
                var args = flatten(p.path("args"), "and");
                if (args.size() != parts.size()) {
                    return false;
                }
                for (int i = 0; i < parts.size(); i++) {
                    if (!scalar(args.get(i), parts.get(i))) {
                        return false;
                    }
                }
                return true;
            }
            default -> {
                return !parts.isEmpty() && scalar(p, parts.size() == 1 ? parts.get(0) : Catalyst.conjunction(parts));
            }
        }
    }

    private boolean aggregate(JsonNode p, LogicalPlan e) {
        if (!(e instanceof Aggregate a) || !rel(p.path("input"), a.child())) {
            return false;
        }
        var inputId = p.path("input").path("id").asText();
        cols.push(cols.input(inputId));
        try {
            return aggregateOver(p, a);
        } finally {
            cols.pop();
        }
    }

    private boolean aggregateOver(JsonNode p, Aggregate a) {
        var groups = Catalyst.list(a.groupingExpressions());
        var groupPatterns = p.path("groups");
        if (groups.size() != groupPatterns.size()) {
            return false;
        }
        for (int i = 0; i < groups.size(); i++) {
            var pattern = groupPatterns.get(i);
            if (isColumn(pattern)) {
                if (!Catalyst.sameAttribute(groups.get(i), one(pattern))) {
                    return false;
                }
            } else {
                var kind = pattern.path("op").asText();
                if (!kind.equals("group_key") && !kind.equals("proj")
                        || !symbol(pattern, groups.get(i), kind.equals("proj") ? projs : groupKeys, null)) {
                    return false;
                }
            }
        }
        var items = Catalyst.list(a.aggregateExpressions());
        var groupOutput = new Attribute[groups.size()];
        var aggItems = new ArrayList<NamedExpression>();
        for (var item : items) {
            var index = groupIndex(item, groups, groupOutput);
            if (index >= 0) {
                groupOutput[index] = item.toAttribute();
            } else {
                aggItems.add(item);
            }
        }
        var aggPatterns = p.path("aggs");
        if (aggItems.size() != aggPatterns.size()) {
            return false;
        }
        var out = new ArrayList<Attribute>();
        for (var g : groupOutput) {
            if (g == null) {
                return false;
            }
            out.add(g);
        }
        for (int k = 0; k < aggItems.size(); k++) {
            var item = aggItems.get(k);
            if (!(item instanceof Alias alias) || !(alias.child() instanceof AggregateExpression ae)
                    || ae.filter().isDefined() || ae.isDistinct() != aggPatterns.get(k).path("distinct").asBoolean()
                    || !symbol(aggPatterns.get(k), ae.aggregateFunction(), aggs, alias)) {
                return false;
            }
            out.add(item.toAttribute());
        }
        cols.put(p.path("id").asText(), out);
        return true;
    }

    private static int groupIndex(NamedExpression item, List<Expression> groups, Attribute[] taken) {
        Expression value = item instanceof Alias a ? a.child() : (Expression) item;
        for (int i = 0; i < groups.size(); i++) {
            var g = groups.get(i);
            if (taken[i] != null) {
                continue;
            }
            if (item instanceof Attribute && Catalyst.sameAttribute(g, (Attribute) item)) {
                return i;
            }
            if (item instanceof Alias && !(value instanceof AggregateExpression) && value.equals(g)) {
                return i;
            }
        }
        return -1;
    }

    private boolean set(JsonNode p, LogicalPlan e) {
        var kind = p.path("kind").asText();
        var all = p.path("all").asBoolean();
        var inputs = p.path("inputs");
        if (kind.equals("union") && !all) {
            var union = distinctChild(e);
            if (union == null) {
                return false;
            }
            return unionAll(p, union, inputs);
        }
        return switch (kind) {
            case "union" -> unionAll(p, e, inputs);
            case "intersect" -> e instanceof Intersect i && i.isAll() == all && rel(inputs.get(0), i.left())
                    && rel(inputs.get(1), i.right()) && setOutput(p, e);
            case "except" -> e instanceof Except x && x.isAll() == all && rel(inputs.get(0), x.left())
                    && rel(inputs.get(1), x.right()) && setOutput(p, e);
            default -> false;
        };
    }

    private boolean unionAll(JsonNode p, LogicalPlan e, JsonNode inputs) {
        if (!(e instanceof Union u)) {
            return false;
        }
        var children = Catalyst.list(u.children());
        if (children.size() != inputs.size()) {
            return false;
        }
        for (int i = 0; i < children.size(); i++) {
            if (!rel(inputs.get(i), children.get(i))) {
                return false;
            }
        }
        return setOutput(p, e);
    }

    private boolean setOutput(JsonNode p, LogicalPlan e) {
        cols.put(p.path("id").asText(), Catalyst.list(e.output()));
        return true;
    }

    private static LogicalPlan distinctChild(LogicalPlan e) {
        if (e instanceof org.apache.spark.sql.catalyst.plans.logical.Distinct d) {
            return d.child();
        }
        if (e instanceof Aggregate a) {
            var output = Catalyst.list(a.child().output());
            var groups = Catalyst.list(a.groupingExpressions());
            var items = Catalyst.list(a.aggregateExpressions());
            if (groups.size() == output.size() && items.size() == output.size()) {
                for (int i = 0; i < output.size(); i++) {
                    if (!Catalyst.sameAttribute(groups.get(i), output.get(i))
                            || !Catalyst.sameAttribute((Expression) items.get(i), output.get(i))) {
                        return null;
                    }
                }
                return a.child();
            }
        }
        return null;
    }

    private boolean empty(JsonNode p, LogicalPlan e) {
        if (!(e instanceof LocalRelation lr) || !lr.data().isEmpty()) {
            return false;
        }
        var output = Catalyst.list(lr.output());
        if (output.size() != p.path("arity").asInt()) {
            return false;
        }
        cols.put(p.path("id").asText(), output);
        if (cols.get(p.path("of").asText()) == null) {
            cols.put(p.path("of").asText(), output);
        }
        return true;
    }

    private boolean values(JsonNode p, LogicalPlan e) {
        if (!(e instanceof LocalRelation lr)) {
            return false;
        }
        var rows = Catalyst.<InternalRow>list(lr.data());
        var patterns = p.path("rows");
        var output = Catalyst.list(lr.output());
        if (rows.size() != patterns.size() || output.size() != patterns.get(0).size()) {
            return false;
        }
        for (int i = 0; i < rows.size(); i++) {
            for (int j = 0; j < output.size(); j++) {
                var cell = patterns.get(i).get(j);
                var type = output.get(j).dataType();
                var value = rows.get(i).isNullAt(j) ? null : rows.get(i).get(j, type);
                if (!literalMatches(cell, new Object[]{value}, type)) {
                    return false;
                }
            }
        }
        cols.put(p.path("id").asText(), output);
        return true;
    }

    private boolean literalMatches(JsonNode cell, Object[] value, DataType type) {
        return switch (cell.path("op").asText()) {
            case "int" -> value[0] instanceof Number n && n.longValue() == cell.path("value").asLong();
            case "true" -> Boolean.TRUE.equals(value[0]);
            case "false" -> Boolean.FALSE.equals(value[0]);
            default -> false;
        };
    }

    static boolean isColumn(JsonNode pattern) {
        var op = pattern.path("op").asText();
        return op.equals("field") || op.equals("joinfield") || op.equals("input") || op.equals("outer")
                || op.equals("local");
    }

    private Attribute one(JsonNode pattern) {
        var attrs = new ArrayList<Attribute>();
        args(List.of(pattern), attrs);
        return attrs.get(0);
    }

    private boolean args(Iterable<JsonNode> args, List<Attribute> out) {
        for (var arg : args) {
            switch (arg.path("op").asText()) {
                case "field" -> {
                    var cols = cols(arg.path("of").asText());
                    out.add(cols.get(arg.path("ordinal").asInt()));
                }
                case "joinfield" -> {
                    var pair = cols.get(Scope.pair(arg.path("left").asText(), arg.path("right").asText()));
                    if (pair == null) {
                        pair = cols.join();
                    }
                    var all = pair != null ? pair : new ArrayList<>(cols(arg.path("left").asText()));
                    if (pair == null) {
                        all.addAll(cols(arg.path("right").asText()));
                    }
                    out.add(all.get(arg.path("ordinal").asInt()));
                }
                case "input" -> out.add(cols.join().get(arg.path("ordinal").asInt()));
                case "outer" -> out.add(cols.outer().get(arg.path("ordinal").asInt()));
                case "local" -> out.add(cols.local().get(arg.path("ordinal").asInt()));
                case "row" -> {
                    var rels = arg.path("rels");
                    var inJoinOrder = rels.size() == 2 ? cols.get(Scope.pair(rels.get(1).asText(), rels.get(0).asText())) : null;
                    if (inJoinOrder != null) {
                        out.addAll(inJoinOrder);
                    } else {
                        for (var rel : rels) {
                            out.addAll(cols(rel.asText()));
                        }
                    }
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean symbol(JsonNode p, Expression e, Map<String, Binding> bindings, Alias alias) {
        var nested = new ArrayList<JsonNode>();
        p.path("args").forEach(nested::add);
        var attrs = new ArrayList<Attribute>();
        if (!args(nested, attrs)) {
            pending.add(new Pending(p, e, bindings, alias));
            return e.deterministic();
        }
        if (!e.deterministic()) {
            return false;
        }
        var allowed = new HashSet<ExprId>();
        attrs.forEach(a -> allowed.add(a.exprId()));
        for (var reference : Catalyst.list(e.references().toSeq())) {
            if (!allowed.contains(reference.exprId()) && !(nested.isEmpty() && !cols.visible(reference.exprId()))) {
                return false;
            }
        }
        return bind(p.path("name").asText(), bindings, new Binding(attrs, e, alias), attrs);
    }

    private boolean bind(String name, Map<String, Binding> bindings, Binding binding, List<Attribute> over) {
        var previous = bindings.get(name);
        if (previous == null) {
            bindings.put(name, binding);
            return true;
        }
        if (previous.params().size() != over.size()) {
            return false;
        }
        return Catalyst.substitute(previous.body(), previous.params(), new ArrayList<Expression>(over))
                .equals(binding.body());
    }

    boolean resolve() {
        for (var use : pending) {
            if (!resolve(use)) {
                return false;
            }
        }
        pending.clear();
        return true;
    }

    private boolean resolve(Pending use) {
        var body = use.expr();
        var params = new ArrayList<Attribute>();
        var direct = new HashSet<ExprId>();
        for (var arg : use.pattern().path("args")) {
            var kind = arg.path("op").asText();
            if (kind.equals("int") || kind.equals("true") || kind.equals("false")) {
                var constant = buildScalar(arg);
                var literalHole = Catalyst.intAttribute("hole").withDataType(constant.dataType());
                var swapped = Catalyst.replace(body, constant, literalHole);
                if (swapped == body) {
                    return false;
                }
                body = swapped;
                params.add(literalHole);
                continue;
            }
            if (kind.equals("scalar")) {
                var inside = subtree(body, arg);
                if (inside == null) {
                    return false;
                }
                var scalarHole = Catalyst.intAttribute("hole").withDataType(inside.dataType());
                body = Catalyst.replace(body, inside, scalarHole);
                params.add(scalarHole);
                continue;
            }
            if (!kind.equals("proj") && !kind.equals("group_key")) {
                var attrs = new ArrayList<Attribute>();
                if (!args(List.of(arg), attrs)) {
                    return false;
                }
                attrs.forEach(a -> direct.add(a.exprId()));
                params.addAll(attrs);
                continue;
            }
            var over = new ArrayList<Attribute>();
            var inner = new ArrayList<JsonNode>();
            arg.path("args").forEach(inner::add);
            if (!args(inner, over)) {
                return false;
            }
            var table = kind.equals("proj") ? projs : groupKeys;
            var known = table.get(arg.path("name").asText());
            Expression value;
            if (known != null) {
                if (known.params().size() != over.size()) {
                    return false;
                }
                value = Catalyst.substitute(known.body(), known.params(), new ArrayList<Expression>(over));
            } else {
                value = null;
                for (var child : Catalyst.<Expression>list(body.children())) {
                    value = value == null ? largest(child, over) : value;
                }
                if (value == null && !(body instanceof org.apache.spark.sql.catalyst.expressions.aggregate.AggregateFunction)) {
                    value = largest(body, over);
                }
                if (value == null) {
                    return false;
                }
                table.put(arg.path("name").asText(), new Binding(over, value, null));
            }
            var hole = Catalyst.intAttribute("hole").withDataType(value.dataType());
            var replaced = Catalyst.replace(body, value, hole);
            if (replaced == body && known == null) {
                return false;
            }
            body = replaced;
            params.add(hole);
        }
        var allowed = new HashSet<ExprId>(direct);
        params.forEach(a -> allowed.add(a.exprId()));
        for (var reference : Catalyst.list(body.references().toSeq())) {
            if (!allowed.contains(reference.exprId())) {
                return false;
            }
        }
        var name = use.pattern().path("name").asText();
        var previous = use.bindings().get(name);
        if (previous == null) {
            use.bindings().put(name, new Binding(params, body, use.alias()));
        }
        return true;
    }

    private Expression subtree(Expression e, JsonNode pattern) {
        for (var child : Catalyst.<Expression>list(e.children())) {
            if (scalar(pattern, child)) {
                return child;
            }
            var found = subtree(child, pattern);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Expression largest(Expression e, List<Attribute> over) {
        var allowed = new HashSet<ExprId>();
        over.forEach(a -> allowed.add(a.exprId()));
        var fits = true;
        for (var reference : Catalyst.list(e.references().toSeq())) {
            fits &= allowed.contains(reference.exprId());
        }
        if (fits && !over.isEmpty() && !e.references().isEmpty()) {
            return e;
        }
        for (var child : Catalyst.<Expression>list(e.children())) {
            var found = largest(child, over);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean scalar(JsonNode p, Expression e) {
        var args = p.path("args");
        switch (p.path("op").asText()) {
            case "true":
                return Catalyst.isTrue(e);
            case "false":
                return Catalyst.isFalse(e);
            case "int":
                return e instanceof Literal l && l.value() instanceof Number n && n.longValue() == p.path("value").asLong()
                        && l.dataType().equals(intType(p));
            case "null":
                return e instanceof Literal l && l.value() == null && l.dataType().equals(nullType(p));
            case "field":
            case "joinfield":
            case "input":
            case "outer":
            case "local":
                return e instanceof Attribute && Catalyst.sameAttribute(e, one(p));
            case "exists":
                return e instanceof Exists exists && subquery(p.path("input"), withJoinCondition(exists.plan(), exists.joinCond()));
            case "in": {
                if (!(e instanceof InSubquery in) || in.values().size() != p.path("values").size()) {
                    return false;
                }
                var values = Catalyst.<Expression>list(in.values());
                for (int i = 0; i < values.size(); i++) {
                    if (!scalar(p.path("values").get(i), values.get(i))) {
                        return false;
                    }
                }
                return subquery(p.path("input"), withJoinCondition(in.query().plan(), in.query().joinCond()));
            }
            case "not":
                return e instanceof Not n && scalar(p.path("arg"), n.child());
            case "and": {
                var parts = Catalyst.conjuncts(e);
                var flat = flatten(args, "and");
                return parts.size() == flat.size() && all(flat, parts);
            }
            case "or": {
                var parts = Catalyst.disjuncts(e);
                var flat = flatten(args, "or");
                return parts.size() == flat.size() && all(flat, parts);
            }
            case "pred":
                return symbol(p, e, preds, null);
            case "proj":
                return symbol(p, e, projs, null);
            case "scalar":
                return operator(p.path("operator").asText(), args, e);
            default:
                return false;
        }
    }

    private static List<JsonNode> flatten(JsonNode args, String op) {
        var out = new ArrayList<JsonNode>();
        for (var arg : args) {
            if (arg.path("op").asText().equals(op)) {
                out.addAll(flatten(arg.path("args"), op));
            } else {
                out.add(arg);
            }
        }
        return out;
    }

    private boolean all(List<JsonNode> args, List<Expression> parts) {
        for (int i = 0; i < parts.size(); i++) {
            if (!scalar(args.get(i), parts.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean truthTest(String operator) {
        return operator.equals("IS TRUE") || operator.equals("IS FALSE") || operator.equals("IS NOT TRUE")
                || operator.equals("IS NOT FALSE");
    }

    private boolean operator(String operator, JsonNode args, Expression e) {
        if (truthTest(operator)) {
            var core = operator.startsWith("IS NOT") ? (e instanceof Not n ? n.child() : null) : e;
            return core instanceof EqualNullSafe test && test.right() instanceof Literal l
                    && Boolean.valueOf(operator.endsWith("TRUE")).equals(l.value()) && args.size() == 1
                    && scalar(args.get(0), test.left());
        }
        Class<?> type = switch (operator) {
            case "=" -> EqualTo.class;
            case "<>" -> Not.class;
            case "<" -> LessThan.class;
            case "<=" -> LessThanOrEqual.class;
            case ">" -> GreaterThan.class;
            case ">=" -> GreaterThanOrEqual.class;
            case "<=>" -> EqualNullSafe.class;
            case "IS NULL" -> IsNull.class;
            case "IS NOT NULL" -> IsNotNull.class;
            case "+" -> org.apache.spark.sql.catalyst.expressions.Add.class;
            case "-" -> args.size() == 1 ? org.apache.spark.sql.catalyst.expressions.UnaryMinus.class
                    : org.apache.spark.sql.catalyst.expressions.Subtract.class;
            case "neg" -> org.apache.spark.sql.catalyst.expressions.UnaryMinus.class;
            case "*" -> org.apache.spark.sql.catalyst.expressions.Multiply.class;
            case "if" -> org.apache.spark.sql.catalyst.expressions.If.class;
            default -> null;
        };
        if (type == null || !type.isInstance(e)) {
            return false;
        }
        var children = Catalyst.<Expression>list(e.children());
        if (operator.equals("<>")) {
            if (!(children.get(0) instanceof EqualTo eq)) {
                return false;
            }
            children = Catalyst.list(eq.children());
        }
        if (children.size() != args.size()) {
            return false;
        }
        for (int i = 0; i < children.size(); i++) {
            if (!scalar(args.get(i), children.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static DataType intType(JsonNode p) {
        return p.path("type").asText().equals("long") ? org.apache.spark.sql.types.LongType$.MODULE$ : IntegerType$.MODULE$;
    }

    private DataType nullType(JsonNode p) {
        return switch (p.path("type").asText()) {
            case "bool" -> BooleanType$.MODULE$;
            case "int" -> IntegerType$.MODULE$;
            default -> {
                for (var ref : p.path("refs")) {
                    var found = cols.get(ref.path("of").asText());
                    if (found != null) {
                        yield found.get(ref.path("ordinal").asInt()).dataType();
                    }
                }
                throw new Abort();
            }
        };
    }

    LogicalPlan build(JsonNode t) {
        var id = t.path("id").asText();
        var plan = switch (t.path("op").asText()) {
            case "scan" -> buildScan(t);
            case "filter" -> {
                var input = build(t.path("input"));
                var inputId = t.path("input").path("id").asText();
                cols.put(id, cols(inputId));
                cols.remember(id, cols.input(inputId));
                cols.push(cols.input(inputId));
                List<Expression> parts;
                try {
                    parts = conditionOf(t.path("cond"));
                } finally {
                    cols.pop();
                }
                yield new Filter(parts.isEmpty() ? Literal$.MODULE$.TrueLiteral() : Catalyst.conjunction(parts), input);
            }
            case "project" -> buildProject(t);
            case "join" -> buildJoin(t);
            case "correlate" -> buildCorrelate(t);
            case "aggregate" -> buildAggregate(t);
            case "set" -> buildSet(t);
            case "empty" -> buildEmpty(t);
            case "values" -> buildValues(t);
            default -> throw new Abort();
        };
        plans.put(id, plan);
        return plan;
    }

    private LogicalPlan buildScan(JsonNode t) {
        var name = t.path("name").asText();
        var seen = scans.get(name);
        if (seen == null || seen.isEmpty()) {
            throw new Abort();
        }
        var use = scanUses.merge(name, 1, Integer::sum) - 1;
        LogicalPlan plan;
        if (use < seen.size()) {
            plan = seen.get(use);
        } else if (seen.get(0) instanceof org.apache.spark.sql.catalyst.analysis.MultiInstanceRelation relation) {
            plan = (LogicalPlan) relation.newInstance();
        } else {
            try {
                plan = Catalyst.fresh(seen.get(0));
            } catch (IllegalArgumentException e) {
                throw new Abort();
            }
        }
        cols.put(t.path("id").asText(), Catalyst.list(plan.output()));
        return plan;
    }

    private LogicalPlan buildProject(JsonNode t) {
        var input = build(t.path("input"));
        var items = new ArrayList<NamedExpression>();
        var out = new ArrayList<Attribute>();
        var inputId = t.path("input").path("id").asText();
        cols.push(cols.input(inputId));
        try {
        for (var pattern : t.path("exprs")) {
            NamedExpression item;
            if (isColumn(pattern)) {
                item = one(pattern);
            } else if (pattern.path("op").asText().equals("proj")) {
                var binding = projection(pattern.path("name").asText());
                var value = symbol(pattern, binding);
                item = named(value, binding.alias());
            } else {
                item = named(buildScalar(pattern), null);
            }
            items.add(item);
            out.add(item.toAttribute());
        }
        } finally {
            cols.pop();
        }
        cols.put(t.path("id").asText(), out);
        return new Project(Catalyst.seq(items), input);
    }

    private NamedExpression named(Expression value, Alias like) {
        if (like != null) {
            var id = usedAliases.add(like.exprId()) ? like.exprId() : NamedExpression$.MODULE$.newExprId();
            return Alias$.MODULE$.apply(value, like.name(), id, like.qualifier(), like.explicitMetadata(),
                    like.nonInheritableMetadataKeys());
        }
        if (value instanceof NamedExpression n) {
            return n;
        }
        return Alias$.MODULE$.apply(value, "col", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private LogicalPlan buildJoin(JsonNode t) {
        var left = build(t.path("left"));
        var leftColumns = cols(t.path("left").path("id").asText());
        var right = distinctFrom(build(t.path("right")), left);
        plans.put(t.path("right").path("id").asText(), right);
        cols.put(t.path("right").path("id").asText(), Catalyst.list(right.output()));
        var rightColumns = cols(t.path("right").path("id").asText());
        cols.remember(t.path("id").asText(), Scope.over(t.path("left").path("id").asText(), leftColumns,
                t.path("right").path("id").asText(), rightColumns));
        cols.push(Scope.over(t.path("left").path("id").asText(), leftColumns, t.path("right").path("id").asText(),
                rightColumns));
        List<Expression> parts;
        try {
            parts = conditionOf(t.path("cond"));
        } finally {
            cols.pop();
        }
        var condition = parts.isEmpty() ? Option.<Expression>empty() : Option.<Expression>apply(Catalyst.conjunction(parts));
        var join = new Join(left, right, joinType(t.path("type").asText()), condition, JoinHint$.MODULE$.NONE());
        cols.put(t.path("id").asText(), Catalyst.list(join.output()));
        return join;
    }

    private static LogicalPlan distinctFrom(LogicalPlan right, LogicalPlan left) {
        if (!(right instanceof Aggregate aggregate)) {
            return right;
        }
        var taken = new HashSet<ExprId>();
        left.output().foreach(a -> taken.add(a.exprId()));
        var changed = false;
        var items = new ArrayList<NamedExpression>();
        for (var item : Catalyst.<NamedExpression>list(aggregate.aggregateExpressions())) {
            if (item instanceof Attribute attr && taken.contains(attr.exprId())) {
                items.add(Alias$.MODULE$.apply(attr, attr.name(), NamedExpression$.MODULE$.newExprId(),
                        Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of())));
                changed = true;
            } else {
                items.add(item);
            }
        }
        return changed ? new Aggregate(aggregate.groupingExpressions(), Catalyst.seq(items), aggregate.child(),
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty()) : right;
    }

    private LogicalPlan buildAggregate(JsonNode t) {
        var input = build(t.path("input"));
        var groups = new ArrayList<Expression>();
        var items = new ArrayList<NamedExpression>();
        var inputId = t.path("input").path("id").asText();
        cols.push(cols.input(inputId));
        try {
        for (var pattern : t.path("groups")) {
            if (isColumn(pattern)) {
                var attr = one(pattern);
                groups.add(attr);
                items.add(attr);
            } else if (!pattern.path("op").asText().equals("proj") && !pattern.path("op").asText().equals("group_key")) {
                var value = buildScalar(pattern);
                groups.add(value);
                items.add(named(value, null));
            } else {
                var binding = groupKey(pattern.path("name").asText());
                var value = symbol(pattern, binding);
                groups.add(value);
                items.add(named(value, binding.alias()));
            }
        }
        for (var pattern : t.path("aggs")) {
            var binding = aggs.get(pattern.path("name").asText());
            if (binding == null) {
                throw new Abort();
            }
            var function = (AggregateFunction) symbol(pattern, binding);
            var expression = AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$,
                    pattern.path("distinct").asBoolean(), Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
            items.add(named(expression, binding.alias()));
        }
        } finally {
            cols.pop();
        }
        var aggregate = new Aggregate(Catalyst.seq(groups), Catalyst.seq(items), input, Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
        cols.put(t.path("id").asText(), Catalyst.list(aggregate.output()));
        return aggregate;
    }

    private LogicalPlan buildSet(JsonNode t) {
        var kind = t.path("kind").asText();
        var all = t.path("all").asBoolean();
        var inputs = new ArrayList<LogicalPlan>();
        t.path("inputs").forEach(i -> inputs.add(build(i)));
        LogicalPlan plan;
        switch (kind) {
            case "union" -> {
                plan = new Union(Catalyst.seq(inputs), false, false);
                if (!all) {
                    var output = Catalyst.<Attribute>list(plan.output());
                    var exprs = new ArrayList<Expression>(output);
                    var named = new ArrayList<NamedExpression>(output);
                    plan = new Aggregate(Catalyst.seq(exprs), Catalyst.seq(named), plan,
                            Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
                }
            }
            case "intersect" -> plan = new Intersect(inputs.get(0), inputs.get(1), all);
            case "except" -> plan = new Except(inputs.get(0), inputs.get(1), all);
            default -> throw new Abort();
        }
        cols.put(t.path("id").asText(), Catalyst.list(plan.output()));
        return plan;
    }

    private LogicalPlan buildEmpty(JsonNode t) {
        var attrs = new ArrayList<Attribute>();
        var like = cols.get(t.path("of").asText());
        for (int i = 0; i < t.path("arity").asInt(); i++) {
            attrs.add(like != null && like.size() == t.path("arity").asInt() ? like.get(i).newInstance()
                    : org.qed.spark.Catalyst.intAttribute("e" + i));
        }
        cols.put(t.path("id").asText(), attrs);
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(List.of()), false, noStream());
    }

    private LogicalPlan buildValues(JsonNode t) {
        var attrs = new ArrayList<Attribute>();
        var first = t.path("rows").get(0);
        for (int j = 0; j < first.size(); j++) {
            var type = first.get(j).path("op").asText().equals("int") ? (DataType) IntegerType$.MODULE$
                    : BooleanType$.MODULE$;
            attrs.add(new org.apache.spark.sql.catalyst.expressions.AttributeReference("col" + j, type, false,
                    org.apache.spark.sql.types.Metadata.empty(), NamedExpression$.MODULE$.newExprId(),
                    Catalyst.<String>seq(List.of())));
        }
        var rows = new ArrayList<InternalRow>();
        for (var row : t.path("rows")) {
            var cells = new Object[row.size()];
            for (int j = 0; j < cells.length; j++) {
                cells[j] = row.get(j).path("op").asText().equals("int") ? (Object) row.get(j).path("value").asInt()
                        : (Object) row.get(j).path("op").asText().equals("true");
            }
            rows.add(new GenericInternalRow(cells));
        }
        cols.put(t.path("id").asText(), attrs);
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.seq(rows), false, noStream());
    }

    static Option<org.apache.spark.sql.connector.read.streaming.SparkDataStream> noStream() {
        return Option.empty();
    }

    private static Expression bool(Expression e) {
        return e instanceof Literal l && l.value() == null && !(l.dataType() instanceof org.apache.spark.sql.types.BooleanType)
                ? new Literal(null, BooleanType$.MODULE$) : e;
    }

    private List<Expression> conditionOf(JsonNode c) {
        return switch (c.path("op").asText()) {
            case "true" -> List.of();
            case "and" -> {
                var parts = new ArrayList<Expression>();
                c.path("args").forEach(a -> parts.add(wrap(bool(buildScalar(a)))));
                yield parts;
            }
            default -> List.of(wrap(bool(buildScalar(c))));
        };
    }

    private Expression wrap(Expression e) {
        return outerIds.isEmpty() ? e : Catalyst.wrapOuter(e, outerIds, outerUsed);
    }

    private Expression buildSubquery(JsonNode p) {
        var values = new ArrayList<Expression>();
        p.path("values").forEach(v -> values.add(buildScalar(v)));
        var outerColumns = cols.join();
        var savedIds = outerIds;
        var savedUsed = outerUsed;
        outerIds = new HashMap<>(savedIds);
        outerColumns.forEach(a -> outerIds.put(a.exprId(), a));
        outerUsed = new ArrayList<>();
        cols.push(Scope.subquery(outerColumns));
        LogicalPlan plan;
        try {
            plan = build(p.path("input"));
        } finally {
            cols.pop();
        }
        var used = new ArrayList<Expression>(outerUsed);
        outerIds = savedIds;
        outerUsed = savedUsed;
        var none = Option.<org.apache.spark.sql.catalyst.plans.logical.HintInfo>empty();
        if (p.path("op").asText().equals("exists")) {
            return new Exists(plan, Catalyst.<Expression>seq(used), NamedExpression$.MODULE$.newExprId(),
                    Catalyst.<Expression>seq(List.of()), none);
        }
        var query = new ListQuery(plan, Catalyst.<Expression>seq(used), NamedExpression$.MODULE$.newExprId(),
                plan.output().size(), Catalyst.<Expression>seq(List.of()), none);
        return new InSubquery(Catalyst.<Expression>seq(values), query);
    }

    private LogicalPlan buildCorrelate(JsonNode t) {
        var left = build(t.path("left"));
        var right = build(t.path("right"));
        var outer = cols(t.path("left").path("id").asText());
        var local = cols(t.path("right").path("id").asText());
        var type = t.path("type").asText();
        var lateral = type.equals("inner") || type.equals("left");
        var savedIds = outerIds;
        var savedUsed = outerUsed;
        outerIds = new HashMap<>();
        outerUsed = new ArrayList<>();
        if (lateral) {
            outer.forEach(a -> outerIds.put(a.exprId(), a));
        }
        List<Expression> parts;
        cols.push(Scope.correlated(outer, local));
        try {
            parts = conditionOf(t.path("cond"));
        } finally {
            cols.pop();
        }
        var used = new ArrayList<Expression>(outerUsed);
        outerIds = savedIds;
        outerUsed = savedUsed;
        var cond = Catalyst.conjunction(parts);
        LogicalPlan plan;
        if (lateral) {
            var subquery = new LateralSubquery(new Filter(cond, right), Catalyst.<Expression>seq(used),
                    NamedExpression$.MODULE$.newExprId(), Catalyst.<Expression>seq(List.of()),
                    Option.<org.apache.spark.sql.catalyst.plans.logical.HintInfo>empty());
            plan = new LateralJoin(left, subquery, joinType(type), Option.<Expression>empty());
        } else {
            plan = new Join(left, right, joinType(type), Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        }
        cols.put(t.path("id").asText(), Catalyst.list(plan.output()));
        return plan;
    }

    private Expression buildScalar(JsonNode p) {
        var args = p.path("args");
        switch (p.path("op").asText()) {
            case "true":
                return Literal$.MODULE$.TrueLiteral();
            case "false":
                return Literal$.MODULE$.FalseLiteral();
            case "int":
                return p.path("type").asText().equals("long") ? Literal$.MODULE$.apply(p.path("value").asLong())
                        : Literal$.MODULE$.apply(p.path("value").asInt());
            case "null":
                return new Literal(null, nullType(p));
            case "field":
            case "joinfield":
            case "input":
            case "outer":
            case "local":
                return one(p);
            case "exists":
            case "in":
                return buildSubquery(p);
            case "not":
                return new Not(bool(buildScalar(p.path("arg"))));
            case "and": {
                var parts = new ArrayList<Expression>();
                args.forEach(a -> parts.add(bool(buildScalar(a))));
                return Catalyst.conjunction(parts);
            }
            case "or": {
                var parts = new ArrayList<Expression>();
                args.forEach(a -> parts.add(bool(buildScalar(a))));
                return Catalyst.disjunction(parts);
            }
            case "pred": {
                var binding = preds.get(p.path("name").asText());
                return symbol(p, binding);
            }
            case "proj": {
                var binding = projection(p.path("name").asText());
                return symbol(p, binding);
            }
            case "group_key": {
                var binding = groupKey(p.path("name").asText());
                return symbol(p, binding);
            }
            case "scalar":
                return buildOperator(p.path("operator").asText(), args);
            default:
                throw new Abort();
        }
    }

    private Binding projection(String name) {
        var found = projs.get(name);
        return found != null ? found : groupKeys.get(name);
    }

    private Binding groupKey(String name) {
        var found = groupKeys.get(name);
        return found != null ? found : projs.get(name);
    }

    private Expression symbol(JsonNode p, Binding binding) {
        if (binding == null) {
            throw new Abort();
        }
        var operands = new ArrayList<Expression>();
        for (var arg : p.path("args")) {
            var attrs = new ArrayList<Attribute>();
            if (args(List.of(arg), attrs)) {
                operands.addAll(attrs);
            } else {
                operands.add(buildScalar(arg));
            }
        }
        if (binding.params().size() != operands.size()) {
            throw new Abort();
        }
        return Catalyst.substitute(binding.body(), binding.params(), operands);
    }

    private Expression buildOperator(String operator, JsonNode args) {
        var a = new ArrayList<Expression>();
        args.forEach(x -> a.add(buildScalar(x)));
        return switch (operator) {
            case "=" -> new EqualTo(a.get(0), a.get(1));
            case "<>" -> new Not(new EqualTo(a.get(0), a.get(1)));
            case "<" -> new LessThan(a.get(0), a.get(1));
            case "<=" -> new LessThanOrEqual(a.get(0), a.get(1));
            case ">" -> new GreaterThan(a.get(0), a.get(1));
            case ">=" -> new GreaterThanOrEqual(a.get(0), a.get(1));
            case "<=>" -> new EqualNullSafe(a.get(0), a.get(1));
            case "IS NULL" -> new IsNull(a.get(0));
            case "IS NOT NULL" -> new IsNotNull(a.get(0));
            case "IS TRUE", "IS FALSE" -> new EqualNullSafe(a.get(0), Literal$.MODULE$.apply(operator.endsWith("TRUE")));
            case "IS NOT TRUE", "IS NOT FALSE" ->
                    new Not(new EqualNullSafe(a.get(0), Literal$.MODULE$.apply(operator.endsWith("TRUE"))));
            case "+" -> Add$.MODULE$.apply(a.get(0), a.get(1), Add$.MODULE$.apply$default$3());
            case "-" -> a.size() == 1 ? UnaryMinus$.MODULE$.apply(a.get(0), UnaryMinus$.MODULE$.apply$default$2())
                    : Subtract$.MODULE$.apply(a.get(0), a.get(1), Subtract$.MODULE$.apply$default$3());
            case "neg" -> UnaryMinus$.MODULE$.apply(a.get(0), UnaryMinus$.MODULE$.apply$default$2());
            case "*" -> Multiply$.MODULE$.apply(a.get(0), a.get(1), Multiply$.MODULE$.apply$default$3());
            case "if" -> new org.apache.spark.sql.catalyst.expressions.If(bool(a.get(0)), a.get(1), a.get(2));
            default -> throw new Abort();
        };
    }

    static JoinType joinType(String name) {
        return switch (name) {
            case "inner" -> Inner$.MODULE$;
            case "left" -> LeftOuter$.MODULE$;
            case "right" -> RightOuter$.MODULE$;
            case "full" -> FullOuter$.MODULE$;
            case "semi" -> LeftSemi$.MODULE$;
            case "anti" -> LeftAnti$.MODULE$;
            default -> throw new Abort();
        };
    }
}
