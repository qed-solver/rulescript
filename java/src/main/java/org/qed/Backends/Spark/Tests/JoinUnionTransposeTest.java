package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Distinct;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinUnionTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinUnionTranspose.json"));

    @Test
    void pushesInnerJoinIntoBothUnionBranches() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var o = Support.relation("o", 1, List.of(), new Random(3));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(x, y)), false, false);
        var uc = Catalyst.<Attribute>list(union.output()).get(0);
        var oc = Catalyst.<Attribute>list(o.output()).get(0);
        var plan = new Join(union, o, Inner$.MODULE$,
                Catalyst.<Expression>some(new GreaterThan(uc, oc)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Union);
        var branches = Catalyst.<LogicalPlan>list(((Union) rewritten).children());
        assertEquals(2, branches.size());
        assertTrue(branches.get(0) instanceof Join left && left.joinType().equals(Inner$.MODULE$)
                && left.left().output().equals(x.output()) && left.right().output().equals(o.output()));
        assertTrue(branches.get(1) instanceof Join right && right.joinType().equals(Inner$.MODULE$)
                && right.left().output().equals(y.output()) && right.right().output().size() == 1);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void pushesLessThanJoinIntoBothUnionBranches() {
        var x = Support.relation("x", 1, List.of(), new Random(4));
        var y = Support.relation("y", 1, List.of(), new Random(5));
        var o = Support.relation("o", 1, List.of(), new Random(6));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(x, y)), false, false);
        var uc = Catalyst.<Attribute>list(union.output()).get(0);
        var oc = Catalyst.<Attribute>list(o.output()).get(0);
        var plan = new Join(union, o, Inner$.MODULE$,
                Catalyst.<Expression>some(new LessThan(uc, oc)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Union);
        var branches = Catalyst.<LogicalPlan>list(((Union) rewritten).children());
        assertEquals(2, branches.size());
        assertTrue(branches.get(0) instanceof Join left && left.joinType().equals(Inner$.MODULE$)
                && left.left().output().equals(x.output()) && left.right().output().equals(o.output()));
        assertTrue(branches.get(1) instanceof Join right && right.joinType().equals(Inner$.MODULE$)
                && right.left().output().equals(y.output()) && right.right().output().size() == 1);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(7));
        var y = Support.relation("y", 1, List.of(), new Random(8));
        var o = Support.relation("o", 1, List.of(), new Random(9));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(x, y)), false, false);
        var uc = Catalyst.<Attribute>list(union.output()).get(0);
        var oc = Catalyst.<Attribute>list(o.output()).get(0);
        var plan = new Join(union, o, LeftOuter$.MODULE$,
                Catalyst.<Expression>some(new GreaterThan(uc, oc)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDistinctUnionAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(10));
        var y = Support.relation("y", 1, List.of(), new Random(11));
        var o = Support.relation("o", 1, List.of(), new Random(12));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(x, y)), false, false);
        var distinct = new Distinct(union);
        var uc = Catalyst.<Attribute>list(distinct.output()).get(0);
        var oc = Catalyst.<Attribute>list(o.output()).get(0);
        var plan = new Join(distinct, o, Inner$.MODULE$,
                Catalyst.<Expression>some(new GreaterThan(uc, oc)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesUnionOnTheRightAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(13));
        var y = Support.relation("y", 1, List.of(), new Random(14));
        var o = Support.relation("o", 1, List.of(), new Random(15));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(x, y)), false, false);
        var uc = Catalyst.<Attribute>list(union.output()).get(0);
        var oc = Catalyst.<Attribute>list(o.output()).get(0);
        var plan = new Join(o, union, Inner$.MODULE$,
                Catalyst.<Expression>some(new GreaterThan(oc, uc)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
