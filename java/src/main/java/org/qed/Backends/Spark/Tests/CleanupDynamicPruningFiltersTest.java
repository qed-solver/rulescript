package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanupDynamicPruningFiltersTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CleanupDynamicPruningFilters.json"));

    @Test
    void removesPruningFilterBelowInnerJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var dpp = new GreaterThan(l0, Literal$.MODULE$.apply(1));
        var extra = new EqualTo(l0, r0);
        var cond = new And(dpp, extra);
        var plan = new Join(new Filter(dpp, left), right, Inner$.MODULE$,
                Catalyst.<Expression>some(cond), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j && j.left() == left && j.right() == right
                && j.joinType().equals(Inner$.MODULE$) && cond.equals(j.condition().get()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void removesPruningFilterWithNonNullKey() {
        var left = Support.relation("l", 1, List.of(0), new Random(3));
        var right = Support.relation("r", 1, List.of(), new Random(4));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var dpp = new GreaterThan(l0, Literal$.MODULE$.apply(0));
        var extra = new EqualTo(l0, r0);
        var cond = new And(dpp, extra);
        var plan = new Join(new Filter(dpp, left), right, Inner$.MODULE$,
                Catalyst.<Expression>some(cond), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertTrue(rewritten instanceof Join j && j.left() == left && cond.equals(j.condition().get()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(5));
        var right = Support.relation("r", 1, List.of(), new Random(6));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var dpp = new GreaterThan(l0, Literal$.MODULE$.apply(1));
        var cond = new And(dpp, new EqualTo(l0, r0));
        var plan = new Join(new Filter(dpp, left), right, LeftOuter$.MODULE$,
                Catalyst.<Expression>some(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinWithoutPruningPredicateAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(7));
        var right = Support.relation("r", 1, List.of(), new Random(8));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var dpp = new GreaterThan(l0, Literal$.MODULE$.apply(1));
        var plan = new Join(new Filter(dpp, left), right, Inner$.MODULE$,
                Catalyst.<Expression>some(new EqualTo(l0, r0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
