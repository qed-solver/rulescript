package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.AttributeReference;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.Metadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateJoinNoColsRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateJoinNoColsRight.json"));

    private static Project zeroColumnOneRow() {
        var value = new AttributeReference("v", BooleanType$.MODULE$, false, Metadata.empty(),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()));
        var values = new LocalRelation(Catalyst.seq(List.of(value)),
                Catalyst.<org.apache.spark.sql.catalyst.InternalRow>seq(List.of(new GenericInternalRow(new Object[]{true}))),
                false, Match.noStream());
        return new Project(Catalyst.<NamedExpression>seq(List.of()), values);
    }

    @Test
    void eliminatesInnerJoinWithOneRowNoColumnRight() {
        var base = Support.relation("l", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var cond = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Join(base, zeroColumnOneRow(), Inner$.MODULE$, Catalyst.<Expression>some(cond),
                JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base && cond.equals(f.condition()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinWithOneRowNoColumnRightAlone() {
        var base = Support.relation("l", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var cond = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Join(base, zeroColumnOneRow(), LeftOuter$.MODULE$, Catalyst.<Expression>some(cond),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoColumnLeftInnerJoinAlone() {
        var base = Support.relation("l", 2, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var cond = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Join(base, zeroColumnOneRow(), Inner$.MODULE$, Catalyst.<Expression>some(cond),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
