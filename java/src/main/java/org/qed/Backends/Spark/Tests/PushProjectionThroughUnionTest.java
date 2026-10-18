package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushProjectionThroughUnionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushProjectionThroughUnion.json"));

    @Test
    void pushesReorderingProjectionIntoBothUnionBranches() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var union = new Union(Catalyst.seq(List.of(left, right)), false, false);
        var u = Catalyst.<Attribute>list(union.output());
        var plan = new Project(Catalyst.seq(List.of(u.get(1))), union);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Union, "expected a Union at the root, got " + rewritten);
        var branches = Catalyst.<LogicalPlan>list(((Union) rewritten).children());
        assertEquals(2, branches.size());
        var leftBranch = branches.get(0);
        var rightBranch = branches.get(1);
        assertTrue(leftBranch instanceof Project lp && lp.child() == left, "left branch: " + leftBranch);
        assertTrue(rightBranch instanceof Project rp && rp.child() == right, "right branch: " + rightBranch);

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void pushesIdentityProjectionIntoBothUnionBranches() {
        var left = Support.relation("l", 2, List.of(0), new Random(3));
        var right = Support.relation("r", 2, List.of(0), new Random(4));
        var union = new Union(Catalyst.seq(List.of(left, right)), false, false);
        var u = Catalyst.<Attribute>list(union.output());
        var plan = new Project(Catalyst.seq(List.of(u.get(0))), union);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Union, "expected a Union at the root, got " + rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesTwoColumnProjectionOverUnionAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(10));
        var right = Support.relation("r", 2, List.of(), new Random(11));
        var union = new Union(Catalyst.seq(List.of(left, right)), false, false);
        var u = Catalyst.<Attribute>list(union.output());
        var plan = new Project(Catalyst.seq(List.of(u.get(1), u.get(0))), union);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectionOverThreeWayUnionAlone() {
        var a = Support.relation("a", 2, List.of(), new Random(5));
        var b = Support.relation("b", 2, List.of(), new Random(6));
        var c = Support.relation("c", 2, List.of(), new Random(7));
        var union = new Union(Catalyst.seq(List.of(a, b, c)), false, false);
        var u = Catalyst.<Attribute>list(union.output());
        var plan = new Project(Catalyst.seq(List.of(u.get(1), u.get(0))), union);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectionOverFilteredUnionAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(8));
        var right = Support.relation("r", 2, List.of(), new Random(9));
        var union = new Union(Catalyst.seq(List.of(left, right)), false, false);
        var u = Catalyst.<Attribute>list(union.output());
        var filtered = new Filter(new GreaterThan(u.get(0), Literal$.MODULE$.apply(2)), union);
        var plan = new Project(Catalyst.seq(List.of(u.get(1), u.get(0))), filtered);
        assertEquals(plan, rule.apply(plan));
    }
}
