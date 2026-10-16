package org.qed.spark;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.InSubquery;
import org.apache.spark.sql.catalyst.expressions.LateralSubquery;
import org.apache.spark.sql.catalyst.expressions.ListQuery;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.GreaterThanOrEqual;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.LessThanOrEqual;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.expressions.aggregate.Min;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.Subtract$;
import org.apache.spark.sql.catalyst.expressions.UnaryMinus$;
import org.apache.spark.sql.catalyst.expressions.Multiply$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Except;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LateralJoin;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.IntegerType$;
import scala.Option;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class Instantiate {
    static final class Unsupported extends RuntimeException {
        Unsupported(String message) {
            super(message);
        }
    }

    private final Random random;
    private final Scope cols = new Scope();
    private final Map<String, LocalRelation> bases = new HashMap<>();
    private Map<org.apache.spark.sql.catalyst.expressions.ExprId, Attribute> outerIds = new HashMap<>();
    private List<Attribute> outerUsed = new ArrayList<>();

    Instantiate(long seed) {
        this.random = new Random(seed);
    }

    LogicalPlan plan(JsonNode p) {
        return build(p);
    }

    private LogicalPlan build(JsonNode p) {
        var id = p.path("id").asText();
        switch (p.path("op").asText()) {
            case "scan": {
                var name = p.path("name").asText();
                var nonNull = new ArrayList<Integer>();
                p.path("nonnull").forEach(n -> nonNull.add(n.asInt()));
                var base = bases.get(name);
                LogicalPlan relation;
                if (base == null) {
                    base = Support.relation(name, p.path("arity").asInt(), nonNull, random);
                    bases.put(name, base);
                    relation = base;
                } else {
                    relation = (LogicalPlan) base.newInstance();
                }
                LogicalPlan plan = p.path("unique").asBoolean() ? unique((LocalRelation) relation) : relation;
                cols.put(id, Catalyst.list(plan.output()));
                return plan;
            }
            case "filter": {
                var input = plan(p.path("input"));
                var inputId = p.path("input").path("id").asText();
                cols.put(id, cols.get(inputId));
                cols.remember(id, cols.input(inputId));
                cols.push(cols.input(inputId));
                try {
                    return new Filter(wrap(conjunction(p.path("cond"))), input);
                } finally {
                    cols.pop();
                }
            }
            case "project": {
                var input = plan(p.path("input"));
                var items = new ArrayList<NamedExpression>();
                var out = new ArrayList<Attribute>();
                var inputId = p.path("input").path("id").asText();
                cols.push(cols.input(inputId));
                for (var e : p.path("exprs")) {
                    NamedExpression item = Match.isColumn(e) ? (Attribute) expr(e)
                            : alias(expr(e), "p" + items.size());
                    items.add(item);
                    out.add(item.toAttribute());
                }
                cols.pop();
                cols.put(id, out);
                return new Project(Catalyst.seq(items), input);
            }
            case "join": {
                var left = plan(p.path("left"));
                var leftColumns = cols.get(p.path("left").path("id").asText());
                var right = plan(p.path("right"));
                var rightColumns = cols.get(p.path("right").path("id").asText());
                cols.remember(id, Scope.over(p.path("left").path("id").asText(), leftColumns,
                        p.path("right").path("id").asText(), rightColumns));
                cols.push(Scope.over(p.path("left").path("id").asText(), leftColumns,
                        p.path("right").path("id").asText(), rightColumns));
                var cond = condition(p.path("cond")).map(c -> wrap(c));
                cols.pop();
                var join = new Join(left, right, Match.joinType(p.path("type").asText()), cond,
                        JoinHint$.MODULE$.NONE());
                cols.put(id, Catalyst.list(join.output()));
                return join;
            }
            case "correlate": {
                var left = plan(p.path("left"));
                var outer = cols.get(p.path("left").path("id").asText());
                var right = plan(p.path("right"));
                var local = cols.get(p.path("right").path("id").asText());
                var type = p.path("type").asText();
                var lateral = type.equals("inner") || type.equals("left");
                var savedIds = outerIds;
                var savedUsed = outerUsed;
                outerIds = new HashMap<>();
                outerUsed = new ArrayList<>();
                if (lateral) {
                    outer.forEach(a -> outerIds.put(a.exprId(), a));
                }
                cols.push(Scope.correlated(outer, local));
                Expression cond;
                try {
                    cond = wrap(expr(p.path("cond")));
                } finally {
                    cols.pop();
                }
                var used = new ArrayList<Expression>(outerUsed);
                outerIds = savedIds;
                outerUsed = savedUsed;
                LogicalPlan correlate;
                if (lateral) {
                    correlate = new LateralJoin(left, new LateralSubquery(new Filter(cond, right),
                            Catalyst.<Expression>seq(used), NamedExpression$.MODULE$.newExprId(),
                            Catalyst.<Expression>seq(List.of()),
                            Option.<org.apache.spark.sql.catalyst.plans.logical.HintInfo>empty()),
                            Match.joinType(type), Option.<Expression>empty());
                } else {
                    correlate = new Join(left, right, Match.joinType(type), Option.<Expression>apply(cond),
                            JoinHint$.MODULE$.NONE());
                }
                cols.put(id, Catalyst.list(correlate.output()));
                return correlate;
            }
            case "aggregate": {
                var input = plan(p.path("input"));
                var groups = new ArrayList<Expression>();
                var items = new ArrayList<NamedExpression>();
                var inputId = p.path("input").path("id").asText();
                cols.push(cols.input(inputId));
                for (var g : p.path("groups")) {
                    var value = expr(g);
                    groups.add(value);
                    items.add(value instanceof Attribute a ? a : alias(value, "g" + items.size()));
                }
                for (var a : p.path("aggs")) {
                    var args = args(a.path("args"));
                    var child = args.isEmpty() ? (Expression) Literal$.MODULE$.apply(1) : sum(args);
                    var function = constant(a.path("name").asText()) % 2 == 0 ? new Max(child) : new Min(child);
                    var aggregate = AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$,
                            a.path("distinct").asBoolean(), Option.<Expression>empty(),
                            NamedExpression$.MODULE$.newExprId());
                    items.add(alias(aggregate, "a" + items.size()));
                }
                cols.pop();
                var aggregate = new Aggregate(Catalyst.seq(groups), Catalyst.seq(items), input,
                        Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
                cols.put(id, Catalyst.list(aggregate.output()));
                return aggregate;
            }
            case "set": {
                var inputs = new ArrayList<LogicalPlan>();
                p.path("inputs").forEach(i -> inputs.add(plan(i)));
                var all = p.path("all").asBoolean();
                LogicalPlan set = switch (p.path("kind").asText()) {
                    case "union" -> new Union(Catalyst.seq(inputs), false, false);
                    case "intersect" -> new Intersect(inputs.get(0), inputs.get(1), all);
                    default -> new Except(inputs.get(0), inputs.get(1), all);
                };
                if (p.path("kind").asText().equals("union") && !all) {
                    var output = Catalyst.<Attribute>list(set.output());
                    set = new Aggregate(Catalyst.seq(new ArrayList<Expression>(output)),
                            Catalyst.seq(new ArrayList<NamedExpression>(output)), set,
                            Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
                }
                cols.put(id, Catalyst.list(set.output()));
                return set;
            }
            case "empty": {
                var attrs = new ArrayList<Attribute>();
                var like = cols.get(p.path("of").asText());
                for (int i = 0; i < p.path("arity").asInt(); i++) {
                    attrs.add(like != null && like.size() == p.path("arity").asInt() ? like.get(i).newInstance()
                            : Support.attribute("e" + i, true));
                }
                cols.put(id, attrs);
                return new LocalRelation(Catalyst.seq(attrs), Catalyst.<org.apache.spark.sql.catalyst.InternalRow>seq(List.of()),
                        false, Match.noStream());
            }
            case "values": {
                var attrs = new ArrayList<Attribute>();
                var first = p.path("rows").get(0);
                for (int j = 0; j < first.size(); j++) {
                    attrs.add(first.get(j).path("op").asText().equals("int") ? Support.attribute("v" + j, false)
                            : new org.apache.spark.sql.catalyst.expressions.AttributeReference("v" + j,
                            BooleanType$.MODULE$, false, org.apache.spark.sql.types.Metadata.empty(),
                            NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of())));
                }
                var rows = new ArrayList<org.apache.spark.sql.catalyst.InternalRow>();
                for (var row : p.path("rows")) {
                    var cells = new Object[row.size()];
                    for (int j = 0; j < cells.length; j++) {
                        cells[j] = row.get(j).path("op").asText().equals("int") ? (Object) row.get(j).path("value").asInt()
                                : (Object) row.get(j).path("op").asText().equals("true");
                    }
                    rows.add(new org.apache.spark.sql.catalyst.expressions.GenericInternalRow(cells));
                }
                cols.put(id, attrs);
                return new LocalRelation(Catalyst.seq(attrs), Catalyst.seq(rows), false, Match.noStream());
            }
            default:
                throw new Unsupported("operator " + p.path("op").asText());
        }
    }

    private LogicalPlan unique(LocalRelation relation) {
        var attrs = Catalyst.<Attribute>list(relation.output());
        var items = new ArrayList<NamedExpression>();
        items.add(attrs.get(0));
        for (int i = 1; i < attrs.size(); i++) {
            var max = AggregateExpression$.MODULE$.apply(new Max(attrs.get(i)), Complete$.MODULE$, false,
                    Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
            items.add(Alias$.MODULE$.apply((Expression) max, attrs.get(i).name(), NamedExpression$.MODULE$.newExprId(),
                    Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of())));
        }
        return new Aggregate(Catalyst.seq(List.<Expression>of(attrs.get(0))), Catalyst.seq(items), relation,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private List<Attribute> cols(JsonNode rel) {
        return cols.get(rel.path("id").asText());
    }

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private Option<Expression> condition(JsonNode c) {
        return c.path("op").asText().equals("true") ? Option.<Expression>empty() : Option.<Expression>apply(conjunction(c));
    }

    private Expression conjunction(JsonNode c) {
        if (c.path("op").asText().equals("and")) {
            var parts = new ArrayList<Expression>();
            c.path("args").forEach(a -> parts.add(expr(a)));
            return Catalyst.conjunction(parts);
        }
        return expr(c);
    }

    private List<Expression> args(JsonNode args) {
        var out = new ArrayList<Expression>();
        for (var arg : args) {
            switch (arg.path("op").asText()) {
                case "row" -> {
                    for (var rel : arg.path("rels")) {
                        out.addAll(cols.get(rel.asText()));
                    }
                }
                default -> out.add(expr(arg));
            }
        }
        return out;
    }

    private Expression wrap(Expression e) {
        return outerIds.isEmpty() ? e : Catalyst.wrapOuter(e, outerIds, outerUsed);
    }

    private Expression subquery(JsonNode p) {
        var values = new ArrayList<Expression>();
        p.path("values").forEach(v -> values.add(expr(v)));
        var outerColumns = cols.join();
        var savedIds = outerIds;
        var savedUsed = outerUsed;
        outerIds = new HashMap<>(savedIds);
        outerColumns.forEach(a -> outerIds.put(a.exprId(), a));
        outerUsed = new ArrayList<>();
        cols.push(Scope.subquery(outerColumns));
        LogicalPlan plan;
        try {
            plan = plan(p.path("input"));
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

    private Expression sum(List<Expression> raw) {
        var parts = new ArrayList<Expression>();
        for (var part : raw) {
            parts.add(part.dataType() instanceof org.apache.spark.sql.types.BooleanType$
                    ? new org.apache.spark.sql.catalyst.expressions.If(part, Literal$.MODULE$.apply(1), Literal$.MODULE$.apply(0))
                    : part);
        }
        var result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            result = Add$.MODULE$.apply(result, parts.get(i), Add$.MODULE$.apply$default$3());
        }
        return result;
    }

    private int constant(String name) {
        return Math.floorMod(name.hashCode(), 4);
    }

    private Expression expr(JsonNode p) {
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
                return new Literal(null, switch (p.path("type").asText()) {
                    case "bool" -> BooleanType$.MODULE$;
                    default -> IntegerType$.MODULE$;
                });
            case "field":
                return cols.get(p.path("of").asText()).get(p.path("ordinal").asInt());
            case "input":
                return cols.join().get(p.path("ordinal").asInt());
            case "outer":
                return cols.outer().get(p.path("ordinal").asInt());
            case "local":
                return cols.local().get(p.path("ordinal").asInt());
            case "exists":
            case "in":
                return subquery(p);
            case "joinfield": {
                var pair = cols.get(Scope.pair(p.path("left").asText(), p.path("right").asText()));
                if (pair == null) {
                    pair = cols.join();
                }
                var all = pair != null ? pair : new ArrayList<>(cols.get(p.path("left").asText()));
                if (pair == null) {
                    all.addAll(cols.get(p.path("right").asText()));
                }
                return all.get(p.path("ordinal").asInt());
            }
            case "not":
                return new Not(expr(p.path("arg")));
            case "and": {
                var parts = new ArrayList<Expression>();
                args.forEach(a -> parts.add(expr(a)));
                return Catalyst.conjunction(parts);
            }
            case "or": {
                var parts = new ArrayList<Expression>();
                args.forEach(a -> parts.add(expr(a)));
                return Catalyst.disjunction(parts);
            }
            case "pred": {
                var operands = args(args);
                if (operands.isEmpty()) {
                    return Literal$.MODULE$.TrueLiteral();
                }
                var value = sum(operands);
                return switch (constant(p.path("name").asText())) {
                    case 0 -> new GreaterThan(value, Literal$.MODULE$.apply(1));
                    case 1 -> new LessThan(value, Literal$.MODULE$.apply(4));
                    case 2 -> new IsNotNull(value);
                    default -> new Not(new EqualTo(value, Literal$.MODULE$.apply(2)));
                };
            }
            case "proj": {
                var operands = args(args);
                var k = Literal$.MODULE$.apply(constant(p.path("name").asText()));
                return operands.isEmpty() ? k : Add$.MODULE$.apply(sum(operands), k, Add$.MODULE$.apply$default$3());
            }
            case "group_key": {
                var operands = args(args);
                return operands.isEmpty() ? Literal$.MODULE$.apply(1) : sum(operands);
            }
            case "scalar": {
                var a = new ArrayList<Expression>();
                args.forEach(x -> a.add(expr(x)));
                return switch (p.path("operator").asText()) {
                    case "=" -> new EqualTo(a.get(0), a.get(1));
                    case "<>" -> new Not(new EqualTo(a.get(0), a.get(1)));
                    case "<" -> new LessThan(a.get(0), a.get(1));
                    case "<=" -> new LessThanOrEqual(a.get(0), a.get(1));
                    case ">" -> new GreaterThan(a.get(0), a.get(1));
                    case ">=" -> new GreaterThanOrEqual(a.get(0), a.get(1));
                    case "<=>" -> new EqualNullSafe(a.get(0), a.get(1));
                    case "IS NULL" -> new IsNull(a.get(0));
                    case "IS NOT NULL" -> new IsNotNull(a.get(0));
                    case "IS TRUE", "IS FALSE" ->
                            new EqualNullSafe(a.get(0), Literal$.MODULE$.apply(p.path("operator").asText().endsWith("TRUE")));
                    case "IS NOT TRUE", "IS NOT FALSE" ->
                            new Not(new EqualNullSafe(a.get(0), Literal$.MODULE$.apply(p.path("operator").asText().endsWith("TRUE"))));
                    case "+" -> Add$.MODULE$.apply(a.get(0), a.get(1), Add$.MODULE$.apply$default$3());
                    case "-" -> a.size() == 1 ? UnaryMinus$.MODULE$.apply(a.get(0), UnaryMinus$.MODULE$.apply$default$2())
                            : Subtract$.MODULE$.apply(a.get(0), a.get(1), Subtract$.MODULE$.apply$default$3());
                    case "neg" -> UnaryMinus$.MODULE$.apply(a.get(0), UnaryMinus$.MODULE$.apply$default$2());
                    case "*" -> Multiply$.MODULE$.apply(a.get(0), a.get(1), Multiply$.MODULE$.apply$default$3());
                    case "if" -> new org.apache.spark.sql.catalyst.expressions.If(a.get(0), a.get(1), a.get(2));
                    default -> throw new Unsupported("operator " + p.path("operator").asText());
                };
            }
            default:
                throw new Unsupported("expression " + p.path("op").asText());
        }
    }
}
