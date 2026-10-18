package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubQueryRemoveTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SubQueryRemove.json"));

    private static Exists existsOver(LogicalPlan plan) {
        return new Exists(plan, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), Option.empty());
    }

    @Test
    void removesUncorrelatedExistsIntoJoinWithDistinctProbe() {
        var outer = Support.relation("r", 1, List.of(), new Random(1));
        var inner = Support.relation("s", 1, List.of(), new Random(2));
        var plan = new Filter(existsOver(inner), outer);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p
                && p.child() instanceof Join j
                && j.left() == outer
                && j.right() instanceof Aggregate);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesNotExistsAlone() {
        var outer = Support.relation("r", 1, List.of(), new Random(1));
        var inner = Support.relation("s", 1, List.of(), new Random(2));
        var plan = new Filter(new Not(existsOver(inner)), outer);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoColumnOuterInputAlone() {
        var outer = Support.relation("r", 2, List.of(), new Random(1));
        var inner = Support.relation("s", 1, List.of(), new Random(2));
        var plan = new Filter(existsOver(inner), outer);
        assertEquals(plan, rule.apply(plan));
    }
}
