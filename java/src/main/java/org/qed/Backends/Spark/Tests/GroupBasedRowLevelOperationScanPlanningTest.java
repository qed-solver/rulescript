package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupBasedRowLevelOperationScanPlanningTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("GroupBasedRowLevelOperationScanPlanning.json"));

    @Test
    void pushesEvaluatedFiltersIntoTargetScanOfInnerJoin() {
        var target = Support.relation("target", 1, List.of(), new Random(1));
        var other = Support.relation("other", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(target.output()).get(0);
        var o = Catalyst.<Attribute>list(other.output()).get(0);
        var eval1 = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var eval2 = new LessThan(x, Literal$.MODULE$.apply(3));
        var rest = new EqualTo(x, o);
        var plan = new Join(target, other, Inner$.MODULE$,
                scala.Option.apply(new And(new And(eval1, eval2), rest)), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var join = assertInstanceOf(Join.class, rewritten);
        var pushed = assertInstanceOf(Filter.class, join.left());
        assertSame(target, pushed.child());
        assertEquals(new And(eval1, eval2), pushed.condition());
        assertSame(other, join.right());
        assertEquals(scala.Option.apply(rest), join.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var target = Support.relation("target", 1, List.of(), new Random(1));
        var other = Support.relation("other", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(target.output()).get(0);
        var o = Catalyst.<Attribute>list(other.output()).get(0);
        var eval1 = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var eval2 = new LessThan(x, Literal$.MODULE$.apply(3));
        var rest = new EqualTo(x, o);
        var plan = new Join(target, other, LeftOuter$.MODULE$,
                scala.Option.apply(new And(new And(eval1, eval2), rest)), JoinHint$.MODULE$.NONE());

        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesConjunctReferencingOtherInputAlone() {
        var target = Support.relation("target", 1, List.of(), new Random(1));
        var other = Support.relation("other", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(target.output()).get(0);
        var o = Catalyst.<Attribute>list(other.output()).get(0);
        var eval1 = new GreaterThan(o, Literal$.MODULE$.apply(2));
        var eval2 = new LessThan(x, Literal$.MODULE$.apply(3));
        var rest = new EqualTo(x, o);
        var plan = new Join(target, other, Inner$.MODULE$,
                scala.Option.apply(new And(new And(eval1, eval2), rest)), JoinHint$.MODULE$.NONE());

        assertEquals(plan, rule.apply(plan));
    }
}
