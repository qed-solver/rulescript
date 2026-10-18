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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateDistinctSetLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateDistinctSetLeft.json"));

    private static Project reorder(LogicalPlan left) {
        var cols = Catalyst.<Attribute>list(left.output());
        return new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(2), cols.get(0), cols.get(1))), left);
    }

    private static LocalRelation emptyRight() {
        var attrs = List.of(Support.attribute("R_0", true), Support.attribute("R_1", true), Support.attribute("R_2", true));
        return new LocalRelation(Catalyst.<Attribute>seq(attrs), Catalyst.<InternalRow>seq(List.of()),
                false, Match.noStream());
    }

    private static Aggregate distinct(Union union) {
        var output = Catalyst.<Attribute>list(union.output());
        return new Aggregate(Catalyst.<Expression>seq(new ArrayList<Expression>(output)),
                Catalyst.<NamedExpression>seq(new ArrayList<NamedExpression>(output)), union,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    @Test
    void distinctUnionWithEmptyRightBecomesDistinctOverLeft() {
        var left = Support.relation("Left", 3, List.of(), new Random(1));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(reorder(left), emptyRight())), false, false);
        var plan = distinct(union);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate a && a.child() instanceof Project p
                && Catalyst.list(a.groupingExpressions()).size() == 3
                && Catalyst.list(a.aggregateExpressions()).size() == 3
                && Catalyst.list(p.output()).size() == 3);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void unionAllWithEmptyRightIsLeftAlone() {
        var left = Support.relation("Left", 3, List.of(), new Random(1));
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(reorder(left), emptyRight())), false, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void distinctUnionWithNonEmptyRightIsLeftAlone() {
        var left = Support.relation("Left", 3, List.of(), new Random(1));
        var right = Support.relation("Right", 3, List.of(), new Random(2));
        var plan = distinct(new Union(Catalyst.<LogicalPlan>seq(List.of(reorder(left), right)), false, false));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void distinctUnionWithIdentityProjectionIsLeftAlone() {
        var left = Support.relation("Left", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(left.output());
        var identity = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(0), cols.get(1), cols.get(2))), left);
        var plan = distinct(new Union(Catalyst.<LogicalPlan>seq(List.of(identity, emptyRight())), false, false));
        assertEquals(plan, rule.apply(plan));
    }
}
