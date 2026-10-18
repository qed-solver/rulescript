package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
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

class FoldInEmptyTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldInEmpty.json"));

    private static LocalRelation emptyRelation(List<Attribute> attrs) {
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    @Test
    void semiJoinAgainstEmptyRightBecomesEmptyLeft() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Support.attribute("r_0", true);
        var right = emptyRelation(List.of(y));
        var plan = new Join(left, right, LeftSemi$.MODULE$,
                Option.<Expression>apply(new EqualTo(x, y)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation lr && lr.data().isEmpty());
        assertEquals(Catalyst.list(left.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void semiJoinWithTwoColumnLeftIsLeftAloneBecauseLeftMustBeArityOne() {
        var left = Support.relation("l", 2, List.of(), new Random(7));
        var x = Catalyst.<Attribute>list(left.output()).get(1);
        var y = Support.attribute("r_0", true);
        var right = emptyRelation(List.of(y));
        var plan = new Join(left, right, LeftSemi$.MODULE$,
                Option.<Expression>apply(new EqualTo(x, y)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void antiJoinAgainstEmptyRightIsLeftAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Support.attribute("r_0", true);
        var right = emptyRelation(List.of(y));
        var plan = new Join(left, right, LeftAnti$.MODULE$,
                Option.<Expression>apply(new EqualTo(x, y)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void semiJoinAgainstNonEmptyRightIsLeftAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(4));
        var right = Support.relation("r", 1, List.of(), new Random(5));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftSemi$.MODULE$,
                Option.<Expression>apply(new EqualTo(x, y)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
