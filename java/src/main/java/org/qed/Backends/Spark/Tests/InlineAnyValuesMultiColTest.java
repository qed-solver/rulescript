package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineAnyValuesMultiColTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("InlineAnyValuesMultiCol.json"));

    private static LocalRelation table(String prefix, boolean nullable, Integer[]... rows) {
        var attrs = new java.util.ArrayList<Attribute>();
        for (int i = 0; i < rows[0].length; i++) {
            attrs.add(Support.attribute(prefix + "_" + i, nullable));
        }
        var data = new java.util.ArrayList<InternalRow>();
        for (var row : rows) {
            data.add(new GenericInternalRow(row));
        }
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(data), false, Match.noStream());
    }

    private static Union values() {
        var first = table("v", false, new Integer[]{1, 2});
        var second = table("w", false, new Integer[]{3, 4});
        return new Union(Catalyst.<LogicalPlan>seq(List.of(first, second)), false, false);
    }

    @Test
    void semiJoinOnBothColumnsBecomesDisjunctiveFilter() {
        var left = table("s", true,
                new Integer[]{1, 2}, new Integer[]{1, 2}, new Integer[]{3, 4},
                new Integer[]{1, 4}, new Integer[]{null, 2}, new Integer[]{0, 0});
        var right = values();
        var s = Catalyst.<Attribute>list(left.output());
        var v = Catalyst.<Attribute>list(right.output());
        var cond = new And(new EqualTo(s.get(0), v.get(0)), new EqualTo(s.get(1), v.get(1)));
        var plan = new Join(left, right, LeftSemi$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child().equals(left) && f.condition() instanceof Or);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(List.of("[1,2]", "[1,2]", "[3,4]"), Support.run(rewritten));
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesSemiJoinOnOnlyFirstColumnAlone() {
        var left = table("s", true, new Integer[]{1, 2}, new Integer[]{3, 4});
        var right = values();
        var s = Catalyst.<Attribute>list(left.output());
        var v = Catalyst.<Attribute>list(right.output());
        var cond = new EqualTo(s.get(0), v.get(0));
        var plan = new Join(left, right, LeftSemi$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAntiJoinOnBothColumnsAlone() {
        var left = table("s", true, new Integer[]{1, 2}, new Integer[]{3, 4});
        var right = values();
        var s = Catalyst.<Attribute>list(left.output());
        var v = Catalyst.<Attribute>list(right.output());
        var cond = new And(new EqualTo(s.get(0), v.get(0)), new EqualTo(s.get(1), v.get(1)));
        var plan = new Join(left, right, LeftAnti$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
