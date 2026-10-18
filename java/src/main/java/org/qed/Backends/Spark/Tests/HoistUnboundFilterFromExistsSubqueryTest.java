package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.HintInfo;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoistUnboundFilterFromExistsSubqueryTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("HoistUnboundFilterFromExistsSubquery.json"));

    private static Exists exists(LogicalPlan plan) {
        return new Exists(plan, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), Option.<HintInfo>empty());
    }

    @Test
    void hoistsOuterOnlyConjunctOutOfExists() {
        var outer = Support.relation("o", 1, List.of(), new Random(1));
        var inner = Support.relation("i", 1, List.of(), new Random(2));
        var y = Catalyst.<Attribute>list(inner.output()).get(0);
        var unbound = new GreaterThan(Literal$.MODULE$.apply(3), Literal$.MODULE$.apply(1));
        var other = new GreaterThan(y, Literal$.MODULE$.apply(2));
        var plan = new Filter(exists(new Filter(new And(unbound, other), inner)), outer);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter top && top.child() == outer);
        var cond = ((Filter) rewritten).condition();
        assertTrue(cond instanceof And a && a.right().equals(unbound) && a.left() instanceof Exists e
                && e.plan() instanceof Filter inside && inside.child() == inner && inside.condition().equals(other));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void hoistsConjunctThatReadsTheOuterRow() {
        var outer = Support.relation("o", 1, List.of(), new Random(1));
        var inner = Support.relation("i", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(outer.output()).get(0);
        var y = Catalyst.<Attribute>list(inner.output()).get(0);
        var unbound = new GreaterThan(new org.apache.spark.sql.catalyst.expressions.OuterReference(x), Literal$.MODULE$.apply(1));
        var other = new GreaterThan(y, Literal$.MODULE$.apply(2));
        var plan = new Filter(exists(new Filter(new And(unbound, other), inner)), outer);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var cond = ((Filter) rewritten).condition();
        assertTrue(cond instanceof And a && a.left() instanceof Exists e
                && e.plan() instanceof Filter inside && inside.condition().equals(other));
    }

    @Test
    void leavesNotExistsAlone() {
        var outer = Support.relation("o", 1, List.of(), new Random(1));
        var inner = Support.relation("i", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(outer.output()).get(0);
        var y = Catalyst.<Attribute>list(inner.output()).get(0);
        var unbound = new GreaterThan(x, Literal$.MODULE$.apply(1));
        var other = new GreaterThan(y, Literal$.MODULE$.apply(2));
        var plan = new Filter(new Not(exists(new Filter(new And(unbound, other), inner))), outer);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDisjunctionInsideExistsAlone() {
        var outer = Support.relation("o", 1, List.of(), new Random(1));
        var inner = Support.relation("i", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(outer.output()).get(0);
        var y = Catalyst.<Attribute>list(inner.output()).get(0);
        var unbound = new GreaterThan(x, Literal$.MODULE$.apply(1));
        var other = new GreaterThan(y, Literal$.MODULE$.apply(2));
        var plan = new Filter(exists(new Filter(new Or(unbound, other), inner)), outer);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesExistsWithoutOuterConjunctAlone() {
        var outer = Support.relation("o", 1, List.of(), new Random(1));
        var inner = Support.relation("i", 1, List.of(), new Random(2));
        var y = Catalyst.<Attribute>list(inner.output()).get(0);
        var other = new GreaterThan(y, Literal$.MODULE$.apply(2));
        var plan = new Filter(exists(new Filter(other, inner)), outer);
        assertEquals(plan, rule.apply(plan));
    }
}
