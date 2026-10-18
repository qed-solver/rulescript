package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.qed.spark.RuleScriptRule;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneJoinRightColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneJoinRightCols.json"));

    private static NamedExpression alias(Expression value) {
        return Alias$.MODULE$.apply(value, "g", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Join join(LogicalPlan left, LogicalPlan right, Expression cond, boolean inner) {
        return new Join(left, right, inner ? Inner$.MODULE$ : LeftOuter$.MODULE$,
                Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void prunesUnusedRightColumn() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 2, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new GreaterThan(l0, r0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(new Add(l0, r0)))),
                join(left, right, cond, true));

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project top && top.child() instanceof Join j
                && j.left().equals(left) && j.right() instanceof Project pruned
                && pruned.child().equals(right)
                && Catalyst.<NamedExpression>list(pruned.projectList()).equals(List.of(r0)));
        assertEquals(Option.apply(cond), ((Join) ((Project) rewritten).child()).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 2, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(new Add(l0, r0)))),
                join(left, right, new GreaterThan(l0, r0), false));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectionOverPrunedColumnAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 2, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var r1 = Catalyst.<Attribute>list(right.output()).get(1);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(new Add(l0, r1)))),
                join(left, right, new GreaterThan(l0, r0), true));
        assertEquals(plan, rule.apply(plan));
    }
}
