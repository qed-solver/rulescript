package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Count;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateProjectConstantToDummyJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateProjectConstantToDummyJoin.json"));

    @Test
    void replacesConstantKeysWithDummyJoin() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var c = Catalyst.<Attribute>list(base.output()).get(0);
        var a0 = alias(Literal$.MODULE$.apply(Boolean.TRUE), "k0");
        var a1 = alias(Literal$.MODULE$.apply(Boolean.FALSE), "k1");
        var project = new Project(Catalyst.<NamedExpression>seq(List.of(a0, a1, c)), base);
        var k0 = a0.toAttribute();
        var k1 = a1.toAttribute();
        var plan = aggregate(project, List.of(k0, k1, c), List.of(k0, k1, c, countAs(c, "f")));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(contains(rewritten, Join.class));
        assertEquals(4, rewritten.output().size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesSwappedConstantsAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var c = Catalyst.<Attribute>list(base.output()).get(0);
        var a0 = alias(Literal$.MODULE$.apply(Boolean.FALSE), "k0");
        var a1 = alias(Literal$.MODULE$.apply(Boolean.TRUE), "k1");
        var project = new Project(Catalyst.<NamedExpression>seq(List.of(a0, a1, c)), base);
        var k0 = a0.toAttribute();
        var k1 = a1.toAttribute();
        var plan = aggregate(project, List.of(k0, k1, c), List.of(k0, k1, c, countAs(c, "f")));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAggregateWithoutThirdGroupKeyAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var c = Catalyst.<Attribute>list(base.output()).get(0);
        var a0 = alias(Literal$.MODULE$.apply(Boolean.TRUE), "k0");
        var a1 = alias(Literal$.MODULE$.apply(Boolean.FALSE), "k1");
        var project = new Project(Catalyst.<NamedExpression>seq(List.of(a0, a1, c)), base);
        var k0 = a0.toAttribute();
        var k1 = a1.toAttribute();
        var plan = aggregate(project, List.of(k0, k1), List.of(k0, k1, countAs(c, "f")));
        assertEquals(plan, rule.apply(plan));
    }

    private static NamedExpression alias(Expression child, String name) {
        return (NamedExpression) Alias$.MODULE$.apply(child, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static NamedExpression countAs(Attribute column, String name) {
        var function = new Count(Catalyst.<Expression>seq(List.of(column)));
        var expression = AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$, false,
                scala.Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return alias(expression, name);
    }

    private static Aggregate aggregate(LogicalPlan child, List<Expression> groups, List<NamedExpression> items) {
        return new Aggregate(Catalyst.<Expression>seq(groups), Catalyst.<NamedExpression>seq(items), child,
                scala.Option.<AggregateHint>empty());
    }

    private static boolean contains(LogicalPlan plan, Class<?> type) {
        if (type.isInstance(plan)) {
            return true;
        }
        return Catalyst.<LogicalPlan>list(plan.children()).stream().anyMatch(child -> contains(child, type));
    }
}
