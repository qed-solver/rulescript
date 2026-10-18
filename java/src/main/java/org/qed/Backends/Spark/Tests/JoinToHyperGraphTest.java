package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinToHyperGraphTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinToHyperGraph.json"));

    private static Attribute first(LocalRelation relation) {
        return Catalyst.<Attribute>list(relation.output()).get(0);
    }

    private static Join inner(LogicalPlan left, LogicalPlan right, Expression cond) {
        return new Join(left, right, Inner$.MODULE$, Catalyst.some(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void rewritesLeftDeepInnerJoinIntoRightDeep() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var va = first(a);
        var vb = first(b);
        var vc = first(c);
        var hyper = inner(a, b, new EqualTo(va, vb));
        var plan = inner(hyper, c, new And(new LessThan(va, vc), new GreaterThan(vb, vc)));

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join top && top.joinType() == Inner$.MODULE$);
        var outer = (Join) rewritten;
        assertEquals(a, outer.left());
        assertTrue(outer.right() instanceof Join inside && inside.left() == b && inside.right() == c);
        assertEquals(plan.output(), rewritten.output());
        assumeExecutable();
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOuterJoinOnTopAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var va = first(a);
        var vb = first(b);
        var vc = first(c);
        var hyper = inner(a, b, new EqualTo(va, vb));
        var plan = new Join(hyper, c, LeftOuter$.MODULE$,
                Catalyst.some(new And(new LessThan(va, vc), new GreaterThan(vb, vc))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    private static void assumeExecutable() {
        Assumptions.assumeTrue(Support.canExecute());
    }
}
