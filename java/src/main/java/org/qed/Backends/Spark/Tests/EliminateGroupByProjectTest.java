package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
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

class EliminateGroupByProjectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateGroupByProject.json"));

    private static Aggregate distinctOn(LogicalPlan child, Attribute key) {
        return new Aggregate(Catalyst.seq(List.<org.apache.spark.sql.catalyst.expressions.Expression>of(key)),
                Catalyst.seq(List.<NamedExpression>of(key)), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    @Test
    void dropsTheProjectUnderDistinctOnTheKeptColumn() {
        var base = Support.relation("S", 2, List.of(), new Random(7));
        var first = Catalyst.<Attribute>list(base.output()).get(0);
        var keep = new Project(Catalyst.seq(List.<NamedExpression>of(first)), base);
        var plan = distinctOn(keep, first);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var expected = distinctOn(base, first);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectKeepingOnlyTheSecondColumnAlone() {
        var base = Support.relation("S", 2, List.of(), new Random(7));
        var second = Catalyst.<Attribute>list(base.output()).get(1);
        var keep = new Project(Catalyst.seq(List.<NamedExpression>of(second)), base);
        var plan = distinctOn(keep, second);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDistinctOverAFilterAlone() {
        var base = Support.relation("S", 2, List.of(), new Random(7));
        var first = Catalyst.<Attribute>list(base.output()).get(0);
        var filtered = new Filter(new org.apache.spark.sql.catalyst.expressions.GreaterThan(first,
                org.apache.spark.sql.catalyst.expressions.Literal$.MODULE$.apply(1)), base);
        var plan = distinctOn(filtered, first);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDistinctOverAThreeColumnProjectAlone() {
        var base = Support.relation("S", 3, List.of(), new Random(7));
        var attrs = Catalyst.<Attribute>list(base.output());
        var keep = new Project(Catalyst.seq(List.<NamedExpression>of(attrs.get(0), attrs.get(1))), base);
        var plan = distinctOn(keep, attrs.get(0));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDistinctOverAProjectWithASynthesizedColumnAlone() {
        var base = Support.relation("S", 2, List.of(), new Random(7));
        var first = Catalyst.<Attribute>list(base.output()).get(0);
        var second = Catalyst.<Attribute>list(base.output()).get(1);
        var sum = new org.apache.spark.sql.catalyst.expressions.Add(first, second);
        var keep = new Project(Catalyst.seq(List.<NamedExpression>of(
                Alias$.MODULE$.apply((Expression) sum, "s", org.apache.spark.sql.catalyst.expressions.NamedExpression$.MODULE$.newExprId(),
                        Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of())))), base);
        var plan = distinctOn(keep, first);
        assertEquals(plan, rule.apply(plan));
    }
}
