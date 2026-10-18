package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FoldNotInEmptyTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNotInEmpty.json"));

    private static LocalRelation emptyLike(LocalRelation relation) {
        return new LocalRelation(relation.output(), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    @Test
    void antiJoinAgainstEmptyRightKeepsEveryLeftRow() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = emptyLike(Support.relation("r", 1, List.of(), new Random(2)));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftAnti$.MODULE$, Catalyst.some(new EqualTo(x, y)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(left, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void antiJoinAgainstNonEmptyRightIsLeftAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftAnti$.MODULE$, Catalyst.some(new EqualTo(x, y)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void semiJoinAgainstEmptyRightIsLeftAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = emptyLike(Support.relation("r", 1, List.of(), new Random(2)));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftSemi$.MODULE$, Catalyst.some(new EqualTo(x, y)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
