package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.RightOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommuteRightJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CommuteRightJoin.json"));

    private static Join rightJoin(int seed) {
        var left = Support.relation("L", 1, List.of(), new Random(seed));
        var right = Support.relation("R", 1, List.of(), new Random(seed + 100));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        return new Join(left, right, RightOuter$.MODULE$, Catalyst.<Expression>some(new GreaterThan(l, r)),
                JoinHint$.MODULE$.NONE());
    }

    @Test
    void rightJoinBecomesProjectedLeftJoinOnSwappedInputs() {
        var plan = rightJoin(1);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project project && project.child() instanceof Join join
                && join.joinType() == LeftOuter$.MODULE$);
        var project = (Project) rewritten;
        var join = (Join) project.child();
        var original = (Join) plan;
        assertEquals(original.right(), join.left());
        assertEquals(original.left(), join.right());
        assertEquals(List.of(original.left().output().apply(0), original.right().output().apply(0)),
                Catalyst.list(project.projectList()));
    }

    @Test
    void rightJoinRewriteReturnsSameRowsForRandomData() {
        var shape = rule.apply(rightJoin(1));
        assertTrue(shape instanceof Project);
        Assumptions.assumeTrue(Support.canExecute());
        for (int seed = 1; seed <= 5; seed++) {
            var plan = rightJoin(seed);
            var rewritten = rule.apply(plan);
            assertEquals(Support.run(plan), Support.run(rewritten));
        }
    }

    @Test
    void leftJoinIsNotRewritten() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftOuter$.MODULE$, Catalyst.<Expression>some(new GreaterThan(l, r)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void rightJoinWithTwoColumnLeftInputIsNotRewritten() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, RightOuter$.MODULE$, Catalyst.<Expression>some(new GreaterThan(l, r)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
