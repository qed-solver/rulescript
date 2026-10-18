package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.LessThanOrEqual;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostBasedJoinReorderTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CostBasedJoinReorder.json"));

    private static Join join(JoinType type, LogicalPlan left, LogicalPlan right, Expression cond) {
        return new Join(left, right, type, Catalyst.some(cond), JoinHint$.MODULE$.NONE());
    }

    private static Expression le(Attribute x, Attribute y) {
        return new LessThanOrEqual(x, y);
    }

    @Test
    void reordersLeftDeepIntoBushy() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var a0 = Catalyst.<Attribute>list(a.output()).get(0);
        var b0 = Catalyst.<Attribute>list(b.output()).get(0);
        var c0 = Catalyst.<Attribute>list(c.output()).get(0);
        var d0 = Catalyst.<Attribute>list(d.output()).get(0);
        var plan = join(Inner$.MODULE$,
                join(Inner$.MODULE$,
                        join(Inner$.MODULE$, a, b, le(a0, b0)),
                        c,
                        new And(le(a0, c0), le(b0, c0))),
                d,
                le(c0, d0));
        var expected = join(Inner$.MODULE$,
                join(Inner$.MODULE$, a, b, le(a0, b0)),
                join(Inner$.MODULE$, c, d, le(c0, d0)),
                new And(le(a0, c0), le(b0, c0)));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOuterJoinAtTheTopAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var a0 = Catalyst.<Attribute>list(a.output()).get(0);
        var b0 = Catalyst.<Attribute>list(b.output()).get(0);
        var c0 = Catalyst.<Attribute>list(c.output()).get(0);
        var d0 = Catalyst.<Attribute>list(d.output()).get(0);
        var plan = join(LeftOuter$.MODULE$,
                join(Inner$.MODULE$,
                        join(Inner$.MODULE$, a, b, le(a0, b0)),
                        c,
                        new And(le(a0, c0), le(b0, c0))),
                d,
                le(c0, d0));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinWithMissingConjunctAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var a0 = Catalyst.<Attribute>list(a.output()).get(0);
        var b0 = Catalyst.<Attribute>list(b.output()).get(0);
        var c0 = Catalyst.<Attribute>list(c.output()).get(0);
        var d0 = Catalyst.<Attribute>list(d.output()).get(0);
        var plan = join(Inner$.MODULE$,
                join(Inner$.MODULE$,
                        join(Inner$.MODULE$, a, b, le(a0, b0)),
                        c,
                        le(a0, c0)),
                d,
                le(c0, d0));
        assertEquals(plan, rule.apply(plan));
    }
}
