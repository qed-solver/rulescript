package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EliminateSemiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateSemiJoin.json"));

    @Test
    void semiJoinOfTheSameRelationOnTrueIsTheRelation() {
        var left = Support.relation("t", 1, List.of(), new Random(1));
        var right = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Join(left, right, LeftSemi$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(left, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void antiJoinOfTheSameRelationIsLeftAlone() {
        var left = Support.relation("t", 1, List.of(), new Random(1));
        var right = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Join(left, right, LeftAnti$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void semiJoinWithAConditionIsLeftAlone() {
        var left = Support.relation("t", 1, List.of(), new Random(1));
        var right = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var condition = Option.<Expression>apply(new GreaterThan(x, Literal$.MODULE$.apply(2)));
        var plan = new Join(left, right, LeftSemi$.MODULE$, condition, JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void semiJoinWithADifferentArityIsLeftAlone() {
        var left = Support.relation("t", 1, List.of(), new Random(1));
        var right = Support.relation("u", 2, List.of(), new Random(1));
        var plan = new Join(left, right, LeftSemi$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
