package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReduceNotNullGroupingColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ReduceNotNullGroupingCols.json"));

    @Test
    void groupingOnDuplicatedNonNullKeyDropsOneGroupingColumn() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var input = new Project(Catalyst.<NamedExpression>seq(List.of(key, key)), base);
        var inputColumns = Catalyst.<Attribute>list(input.output());
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(inputColumns.get(0), inputColumns.get(1))),
                Catalyst.<NamedExpression>seq(List.of(inputColumns.get(0), inputColumns.get(1))), input, scala.Option.empty());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var child = ((Project) rewritten).child();
        assertTrue(child instanceof Aggregate);
        assertEquals(1, Catalyst.list(((Aggregate) child).groupingExpressions()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void groupingOnNullableDuplicatedKeyIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var input = new Project(Catalyst.<NamedExpression>seq(List.of(key, key)), base);
        var inputColumns = Catalyst.<Attribute>list(input.output());
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(inputColumns.get(0), inputColumns.get(1))),
                Catalyst.<NamedExpression>seq(List.of(inputColumns.get(0), inputColumns.get(1))), input, scala.Option.empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void groupingOnTwoDifferentColumnsIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(3));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(columns.get(0), columns.get(1))),
                Catalyst.<NamedExpression>seq(List.of(columns.get(0), columns.get(1))), base, scala.Option.empty());
        assertEquals(plan, rule.apply(plan));
    }
}
