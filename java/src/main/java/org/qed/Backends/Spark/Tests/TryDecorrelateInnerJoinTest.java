package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryDecorrelateInnerJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("TryDecorrelateInnerJoin.json"));

    private static Join join(LogicalPlan left, LogicalPlan right, org.apache.spark.sql.catalyst.plans.JoinType type,
                             Expression cond) {
        return new Join(left, right, type, Option.apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void pullsInnerConditionUpIntoOuterJoin() {
        var l = Support.relation("L", 1, List.of(), new Random(1));
        var il = Support.relation("IL", 1, List.of(), new Random(2));
        var ir = Support.relation("IR", 1, List.of(), new Random(3));
        Attribute lc = Catalyst.<Attribute>list(l.output()).get(0);
        Attribute ilc = Catalyst.<Attribute>list(il.output()).get(0);
        Attribute irc = Catalyst.<Attribute>list(ir.output()).get(0);
        var innerBefore = join(il, ir, Inner$.MODULE$, new LessThan(ilc, irc));
        var plan = join(l, innerBefore, Inner$.MODULE$, new EqualTo(lc, ilc));
        var expected = join(l, join(il, ir, Inner$.MODULE$, null),
                Inner$.MODULE$, new And(new EqualTo(lc, ilc), new LessThan(ilc, irc)));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOuterJoinOfInnerConditionAlone() {
        var l = Support.relation("L", 1, List.of(), new Random(1));
        var il = Support.relation("IL", 1, List.of(), new Random(2));
        var ir = Support.relation("IR", 1, List.of(), new Random(3));
        Attribute lc = Catalyst.<Attribute>list(l.output()).get(0);
        Attribute ilc = Catalyst.<Attribute>list(il.output()).get(0);
        Attribute irc = Catalyst.<Attribute>list(ir.output()).get(0);
        var innerBefore = join(il, ir, LeftOuter$.MODULE$, new LessThan(ilc, irc));
        var plan = join(l, innerBefore, Inner$.MODULE$, new EqualTo(lc, ilc));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinWithWiderInnerLeftAlone() {
        var l = Support.relation("L", 1, List.of(), new Random(1));
        var il = Support.relation("IL", 2, List.of(), new Random(2));
        var ir = Support.relation("IR", 1, List.of(), new Random(3));
        Attribute lc = Catalyst.<Attribute>list(l.output()).get(0);
        Attribute ilc = Catalyst.<Attribute>list(il.output()).get(0);
        Attribute irc = Catalyst.<Attribute>list(ir.output()).get(0);
        var innerBefore = join(il, ir, Inner$.MODULE$, new LessThan(ilc, irc));
        var plan = join(l, innerBefore, Inner$.MODULE$, new EqualTo(lc, ilc));
        assertEquals(plan, rule.apply(plan));
    }
}
