package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateAntiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateAntiJoin.json"));

    private static LocalRelation emptyRelation(String name, int arity) {
        var attrs = new java.util.ArrayList<Attribute>();
        for (int i = 0; i < arity; i++) {
            attrs.add(Support.attribute(name + "_" + i, false));
        }
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    @Test
    void antiJoinAgainstEmptyRightReturnsTheLeftRows() {
        var left = Support.relation("L", 1, List.of(), new Random(7));
        var right = emptyRelation("R", 1);
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftAnti$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(new EqualTo(l, r)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation);
        assertEquals(left, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void antiJoinAgainstNonEmptyRightIsLeftAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(7));
        var right = Support.relation("R", 1, List.of(), new Random(8));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftAnti$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(new EqualTo(l, r)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leftOuterJoinAgainstEmptyRightIsLeftAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(7));
        var right = emptyRelation("R", 1);
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftOuter$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(new EqualTo(l, r)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void semiJoinAgainstEmptyRightIsLeftAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(7));
        var right = emptyRelation("R", 1);
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftSemi$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(new EqualTo(l, r)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
