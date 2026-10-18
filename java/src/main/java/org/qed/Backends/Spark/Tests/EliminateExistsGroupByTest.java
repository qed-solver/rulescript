package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import scala.Option;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EliminateExistsGroupByTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateExistsGroupBy.json"));

    @Test
    void dropsGroupByUnderUncorrelatedSemiJoin() {
        LocalRelation outer = Support.relation("o", 1, List.of(), new Random(1));
        LocalRelation input = Support.relation("i", 1, List.of(), new Random(2));
        Attribute x = Catalyst.<Attribute>list(input.output()).get(0);
        var grouped = new Aggregate(Catalyst.<Expression>seq(List.of(x)), Catalyst.seq(List.of(x)), input,
                Option.<AggregateHint>empty());
        var plan = new Join(outer, grouped, Match.joinType("semi"),
                Catalyst.some((Expression) Literal$.MODULE$.TrueLiteral()), JoinHint$.MODULE$.NONE());
        var expected = new Join(outer, input, Match.joinType("semi"),
                Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAntiJoinOverGroupByAlone() {
        LocalRelation outer = Support.relation("o", 1, List.of(), new Random(1));
        LocalRelation input = Support.relation("i", 1, List.of(), new Random(2));
        Attribute x = Catalyst.<Attribute>list(input.output()).get(0);
        var grouped = new Aggregate(Catalyst.<Expression>seq(List.of(x)), Catalyst.seq(List.of(x)), input,
                Option.<AggregateHint>empty());
        var plan = new Join(outer, grouped, Match.joinType("anti"),
                Catalyst.some((Expression) Literal$.MODULE$.TrueLiteral()), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesSemiJoinWithNonTrueConditionAlone() {
        LocalRelation outer = Support.relation("o", 1, List.of(), new Random(1));
        LocalRelation input = Support.relation("i", 1, List.of(), new Random(2));
        Attribute x = Catalyst.<Attribute>list(input.output()).get(0);
        var grouped = new Aggregate(Catalyst.<Expression>seq(List.of(x)), Catalyst.seq(List.of(x)), input,
                Option.<AggregateHint>empty());
        var condition = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Join(outer, grouped, Match.joinType("semi"), Catalyst.some((Expression) condition),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesSemiJoinOverProjectionAlone() {
        LocalRelation outer = Support.relation("o", 1, List.of(), new Random(1));
        LocalRelation input = Support.relation("i", 1, List.of(), new Random(2));
        Attribute x = Catalyst.<Attribute>list(input.output()).get(0);
        var projected = new Project(Catalyst.seq(List.of(x)), input);
        var plan = new Join(outer, projected, Match.joinType("semi"),
                Catalyst.some((Expression) Literal$.MODULE$.TrueLiteral()), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
