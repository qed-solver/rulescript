package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateDistinctSetRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateDistinctSetRight.json"));

    private static LocalRelation emptyRelation(String prefix) {
        var attrs = new ArrayList<Attribute>();
        for (int i = 0; i < 3; i++) {
            attrs.add(Support.attribute(prefix + i, true));
        }
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    private static Project reordered(LogicalPlan input) {
        var cols = Catalyst.<Attribute>list(input.output());
        var items = List.<NamedExpression>of(cols.get(2), cols.get(0), cols.get(1));
        return new Project(Catalyst.seq(items), input);
    }

    private static Aggregate distinct(LogicalPlan child) {
        var output = new ArrayList<Attribute>(Catalyst.<Attribute>list(child.output()));
        return new Aggregate(Catalyst.seq(new ArrayList<Expression>(output)),
                Catalyst.seq(new ArrayList<NamedExpression>(output)), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    @Test
    void distinctUnionWithEmptyLeftBecomesDistinctOverRight() {
        var right = Support.relation("r", 3, List.of(), new Random(7));
        var projected = reordered(right);
        var union = new Union(Catalyst.seq(List.<LogicalPlan>of(projected, emptyRelation("e"))), false, false);
        var plan = distinct(union);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var aggregate = assertInstanceOf(Aggregate.class, rewritten);
        assertInstanceOf(Project.class, aggregate.child());
        assertEquals(Catalyst.list(projected.output()), Catalyst.list(aggregate.groupingExpressions()));
        assertEquals(Catalyst.list(projected.output()), Catalyst.list(aggregate.aggregateExpressions()));
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
    }

    @Test
    void distinctUnionWithEmptyLeftKeepsRowsOnSeveralInstances() {
        Assumptions.assumeTrue(Support.canExecute());
        for (int seed = 1; seed <= 5; seed++) {
            var right = Support.relation("r", 3, List.of(), new Random(seed));
            var union = new Union(Catalyst.seq(List.<LogicalPlan>of(reordered(right), emptyRelation("e"))), false, false);
            var plan = distinct(union);
            var rewritten = rule.apply(plan);
            assertNotEquals(plan, rewritten);
            assertEquals(Support.run(plan), Support.run(rewritten));
        }
    }

    @Test
    void distinctUnionWithNonEmptyLeftIsLeftAlone() {
        var right = Support.relation("r", 3, List.of(), new Random(7));
        var left = Support.relation("l", 3, List.of(), new Random(3));
        var union = new Union(Catalyst.seq(List.<LogicalPlan>of(reordered(right), left)), false, false);
        var plan = distinct(union);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void unionAllWithEmptyLeftIsLeftAlone() {
        var right = Support.relation("r", 3, List.of(), new Random(7));
        var union = new Union(Catalyst.seq(List.<LogicalPlan>of(reordered(right), emptyRelation("e"))), false, false);
        assertEquals(union, rule.apply(union));
    }
}
