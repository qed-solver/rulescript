package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinProjectTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinProjectTranspose.json"));

    private static Expression sum(Attribute a, Attribute b) {
        return Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3());
    }

    private static Project projectSum(String name, Expression value, LogicalPlan child) {
        var item = Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        return new Project(Catalyst.seq(List.of(item)), child);
    }

    @Test
    void pushesTheConditionBelowTheProjectionsAndKeepsTheOutput() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var leftProject = projectSum("tl", sum(l.get(0), l.get(1)), left);
        var rightProject = projectSum("tr", sum(r.get(0), r.get(1)), right);
        var tl = Catalyst.<Attribute>list(leftProject.output()).get(0);
        var tr = Catalyst.<Attribute>list(rightProject.output()).get(0);
        var plan = new Join(leftProject, rightProject, Match.joinType("inner"),
                Option.<Expression>apply(new GreaterThan(tl, tr)), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        assertTrue(rewritten instanceof Project p && p.child() instanceof Join j
                && j.left().equals(left) && j.right().equals(right)
                && j.joinType().equals(Match.joinType("inner"))
                && j.condition().equals(Option.<Expression>apply(new GreaterThan(sum(l.get(0), l.get(1)), sum(r.get(0), r.get(1))))));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(3));
        var right = Support.relation("R", 2, List.of(), new Random(4));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var leftProject = projectSum("tl", sum(l.get(0), l.get(1)), left);
        var rightProject = projectSum("tr", sum(r.get(0), r.get(1)), right);
        var tl = Catalyst.<Attribute>list(leftProject.output()).get(0);
        var tr = Catalyst.<Attribute>list(rightProject.output()).get(0);
        var plan = new Join(leftProject, rightProject, Match.joinType("left"),
                Option.<Expression>apply(new GreaterThan(tl, tr)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAJoinWithAPlainScanOnOneSideAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(5));
        var right = Support.relation("R", 2, List.of(), new Random(6));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var leftProject = projectSum("tl", sum(l.get(0), l.get(1)), left);
        var tl = Catalyst.<Attribute>list(leftProject.output()).get(0);
        var plan = new Join(leftProject, right, Match.joinType("inner"),
                Option.<Expression>apply(new GreaterThan(tl, r.get(0))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
