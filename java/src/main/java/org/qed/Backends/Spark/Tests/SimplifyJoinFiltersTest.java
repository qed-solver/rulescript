package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyJoinFiltersTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyJoinFilters.json"));

    @Test
    void dropsTrueFromInnerJoinCondition() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var predicate = new GreaterThan(l, r);
        var condition = new And(predicate, Literal$.MODULE$.TrueLiteral());
        var plan = new Join(left, right, Match.joinType("inner"), Catalyst.some(condition), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var join = assertInstanceOf(Join.class, rewritten);
        assertEquals(Match.joinType("inner"), join.joinType());
        assertEquals(left, join.left());
        assertEquals(right, join.right());
        assertEquals(predicate, join.condition().get());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var condition = new And(new GreaterThan(l, r), Literal$.MODULE$.TrueLiteral());
        var plan = new Join(left, right, Match.joinType("left"), Catalyst.some(condition), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
