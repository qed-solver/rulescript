package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractPythonUDFFromJoinConditionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ExtractPythonUDFFromJoinCondition.json"));

    @Test
    void liftsLeftOnlyConjunctIntoFilterAboveInnerJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var udf = new GreaterThan(x, Literal$.MODULE$.apply(1));
        var rest = new LessThan(x, Literal$.MODULE$.apply(3));
        var plan = new Join(left, right, Inner$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(new And(udf, rest)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.condition().equals(udf)
                && f.child() instanceof Join j && j.left() == left && j.right() == right
                && j.joinType() == Inner$.MODULE$ && j.condition().isDefined() && j.condition().get().equals(rest));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var cond = new And(new GreaterThan(x, Literal$.MODULE$.apply(1)), new LessThan(x, Literal$.MODULE$.apply(3)));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesConditionThatReadsRightSideInFirstConjunctAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new GreaterThan(y, Literal$.MODULE$.apply(1)), new LessThan(x, Literal$.MODULE$.apply(3)));
        var plan = new Join(left, right, Inner$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
