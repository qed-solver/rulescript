package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.UnaryMinus$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractGroupingPythonUDFFromAggregateTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("ExtractGroupingPythonUDFFromAggregate.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression maxOf(Expression child, boolean distinct) {
        return AggregateExpression$.MODULE$.apply(new Max(child), Complete$.MODULE$, distinct,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
    }

    private static Expression plusOne(Attribute x) {
        return UnaryMinus$.MODULE$.apply(x, false);
    }

    private static Aggregate aggregate(List<Expression> groups, List<NamedExpression> items, LogicalPlan child) {
        return new Aggregate(Catalyst.seq(groups), Catalyst.seq(items), child,
                Option.<AggregateHint>empty());
    }

    @Test
    void extractsGroupingFunctionBelowAggregate() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var key = plusOne(x);
        var plan = aggregate(List.of(key), List.of(alias(key, "k"), alias(maxOf(x, false), "m")), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        assertTrue(rewritten instanceof Project);
        var restore = (Project) rewritten;
        assertTrue(restore.child() instanceof Aggregate);
        var top = (Aggregate) restore.child();
        assertTrue(top.child() instanceof Project);
        var project = (Project) top.child();
        assertEquals(2, Catalyst.list(project.projectList()).size());
        assertTrue(project.child() == base);
        assertTrue(Catalyst.list(top.groupingExpressions()).get(0) instanceof Attribute);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesDistinctAggregateAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var key = plusOne(x);
        var plan = aggregate(List.of(key), List.of(alias(key, "k"), alias(maxOf(x, true), "m")), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoColumnInputAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var columns = Catalyst.<Attribute>list(base.output());
        var key = plusOne(columns.get(0));
        var plan = aggregate(List.of(key), List.of(alias(key, "k"), alias(maxOf(columns.get(1), false), "m")), base);
        assertEquals(plan, rule.apply(plan));
    }
}
