package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.AttributeReference;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.ExprId;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.trees.TreeNode;
import scala.Option;
import scala.collection.immutable.Seq;
import scala.jdk.javaapi.CollectionConverters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class Catalyst {
    private Catalyst() { }

    static <T> List<T> list(scala.collection.Seq<T> seq) {
        return new ArrayList<>(CollectionConverters.asJava(seq));
    }

    static <T> Seq<T> seq(List<? extends T> list) {
        return CollectionConverters.<T>asScala(new ArrayList<T>(list)).toSeq();
    }

    static <T> Option<T> some(T value) {
        return Option.apply(value);
    }

    static Attribute intAttribute(String name) {
        return new AttributeReference(name, org.apache.spark.sql.types.IntegerType$.MODULE$, true,
                org.apache.spark.sql.types.Metadata.empty(),
                org.apache.spark.sql.catalyst.expressions.NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()));
    }

    static Expression replace(Expression e, Expression target, Expression replacement) {
        if (e.equals(target)) {
            return replacement;
        }
        var children = Catalyst.<Expression>list(e.children());
        var changed = false;
        var updated = new ArrayList<Expression>();
        for (var child : children) {
            var next = replace(child, target, replacement);
            changed |= next != child;
            updated.add(next);
        }
        return changed ? e.withNewChildren(Catalyst.<Expression>seq(updated)) : e;
    }

    static LogicalPlan fresh(LogicalPlan plan) {
        return fresh(plan, new java.util.HashMap<>());
    }

    private static LogicalPlan fresh(LogicalPlan plan, Map<ExprId, Expression> renamed) {
        if (plan instanceof org.apache.spark.sql.catalyst.analysis.MultiInstanceRelation relation) {
            var copy = (LogicalPlan) relation.newInstance();
            var before = Catalyst.<Attribute>list(plan.output());
            var after = Catalyst.<Attribute>list(copy.output());
            for (int i = 0; i < before.size(); i++) {
                renamed.put(before.get(i).exprId(), after.get(i));
            }
            return copy;
        }
        if (plan.children().size() != 1) {
            throw new IllegalArgumentException("cannot renew " + plan.nodeName());
        }
        var child = fresh(plan.children().head(), renamed);
        if (plan instanceof Filter filter) {
            return new Filter(transformAttributes(filter.condition(), renamed), child);
        }
        var items = new ArrayList<NamedExpression>();
        var list = plan instanceof Project project ? list(project.projectList())
                : plan instanceof Aggregate aggregate ? list(aggregate.aggregateExpressions()) : null;
        if (list == null) {
            throw new IllegalArgumentException("cannot renew " + plan.nodeName());
        }
        for (var item : list) {
            var moved = (NamedExpression) transformAttributes((Expression) item, renamed);
            if (moved instanceof org.apache.spark.sql.catalyst.expressions.Alias) {
                moved = moved.newInstance();
            }
            renamed.put(item.exprId(), moved.toAttribute());
            items.add(moved);
        }
        if (plan instanceof Project) {
            return new Project(seq(items), child);
        }
        var aggregate = (Aggregate) plan;
        var groups = new ArrayList<Expression>();
        for (var group : list(aggregate.groupingExpressions())) {
            groups.add(transformAttributes(group, renamed));
        }
        return new Aggregate(seq(groups), seq(items), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    static Expression stripOuter(Expression e) {
        if (e instanceof org.apache.spark.sql.catalyst.expressions.OuterReference o) {
            return o.e().toAttribute();
        }
        var children = Catalyst.<Expression>list(e.children());
        var changed = false;
        var updated = new ArrayList<Expression>();
        for (var child : children) {
            var next = stripOuter(child);
            changed |= next != child;
            updated.add(next);
        }
        return changed ? e.withNewChildren(Catalyst.<Expression>seq(updated)) : e;
    }

    static Expression wrapOuter(Expression e, Map<ExprId, Attribute> outer, List<Attribute> used) {
        if (e instanceof AttributeReference a && outer.containsKey(a.exprId())) {
            if (used.stream().noneMatch(u -> u.exprId().equals(a.exprId()))) {
                used.add(a);
            }
            return new org.apache.spark.sql.catalyst.expressions.OuterReference(a);
        }
        var children = Catalyst.<Expression>list(e.children());
        var changed = false;
        var updated = new ArrayList<Expression>();
        for (var child : children) {
            var next = wrapOuter(child, outer, used);
            changed |= next != child;
            updated.add(next);
        }
        return changed ? e.withNewChildren(Catalyst.<Expression>seq(updated)) : e;
    }

    static boolean isTrue(Expression e) {
        return e instanceof Literal literal && Boolean.TRUE.equals(literal.value());
    }

    static boolean isFalse(Expression e) {
        return e instanceof Literal literal && Boolean.FALSE.equals(literal.value());
    }

    static List<Expression> conjuncts(Expression e) {
        var out = new ArrayList<Expression>();
        flatten(e, true, out);
        return out;
    }

    static List<Expression> disjuncts(Expression e) {
        var out = new ArrayList<Expression>();
        flatten(e, false, out);
        return out;
    }

    private static void flatten(Expression e, boolean and, List<Expression> out) {
        if (and && e instanceof And a) {
            flatten(a.left(), true, out);
            flatten(a.right(), true, out);
        } else if (!and && e instanceof Or o) {
            flatten(o.left(), false, out);
            flatten(o.right(), false, out);
        } else {
            out.add(e);
        }
    }

    static Expression conjunction(List<Expression> parts) {
        return fold(parts, true);
    }

    static Expression disjunction(List<Expression> parts) {
        return fold(parts, false);
    }

    private static Expression fold(List<Expression> parts, boolean and) {
        var result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            result = and ? new And(result, parts.get(i)) : new Or(result, parts.get(i));
        }
        return result;
    }

    static boolean sameAttribute(Expression e, Attribute a) {
        return e instanceof Attribute x && x.exprId().equals(a.exprId());
    }

    static ExprId id(Attribute a) {
        return a.exprId();
    }

    @SuppressWarnings("unchecked")
    static <T extends TreeNode<?>> T transformAttributes(T tree, Map<ExprId, Expression> replacements) {
        Function<Expression, Expression> f = new Function<>() {
            @Override
            public Expression apply(Expression x) {
                if (x instanceof AttributeReference a && replacements.containsKey(a.exprId())) {
                    return replacements.get(a.exprId());
                }
                var children = Catalyst.<Expression>list((scala.collection.Seq<Expression>) (Object) x.children());
                if (children.isEmpty()) {
                    return x;
                }
                var changed = false;
                var updated = new ArrayList<Expression>();
                for (var child : children) {
                    var next = apply(child);
                    changed |= next != child;
                    updated.add(next);
                }
                return changed ? x.withNewChildren(Catalyst.<Expression>seq(updated)) : x;
            }
        };
        return (T) f.apply((Expression) tree);
    }

    static Expression substitute(Expression e, List<Attribute> from, List<Expression> to) {
        var replacements = new java.util.HashMap<ExprId, Expression>();
        for (int i = 0; i < from.size(); i++) {
            replacements.put(from.get(i).exprId(), to.get(i));
        }
        return transformAttributes(e, replacements);
    }
}
