package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.Or;
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

class PushdownPredicatesAndPruneColumnsForCTEDefTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushdownPredicatesAndPruneColumnsForCTEDef.json"));

    @Test
    void pushesBothReferencePredicatesIntoTheDefinition() {
        var cte = Support.relation("cte", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(cte.output()).get(0);
        var ref1 = new GreaterThan(x, Literal$.MODULE$.apply(1));
        var ref2 = new LessThan(x, Literal$.MODULE$.apply(3));
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(
                new Filter(ref1, cte), new Filter(ref2, cte))), false, false);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Union);
        var children = Catalyst.<LogicalPlan>list(((Union) rewritten).children());
        assertEquals(2, children.size());
        var disjunction = new Or(ref1, ref2);
        var pushed = new Filter(disjunction, cte);
        assertEquals(new Filter(ref1, pushed), children.get(0));
        assertEquals(new Filter(ref2, pushed), children.get(1));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAUnionWithAProjectedSecondBranchAlone() {
        var cte = Support.relation("cte", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(cte.output()).get(0);
        var ref1 = new GreaterThan(x, Literal$.MODULE$.apply(1));
        var ref2 = new LessThan(x, Literal$.MODULE$.apply(3));
        var secondBranch = new Project(Catalyst.<NamedExpression>seq(List.of(x)), new Filter(ref2, cte));
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(new Filter(ref1, cte), secondBranch)), false, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAUnionWithThreeBranchesAlone() {
        var cte = Support.relation("cte", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(cte.output()).get(0);
        var ref1 = new GreaterThan(x, Literal$.MODULE$.apply(1));
        var ref2 = new LessThan(x, Literal$.MODULE$.apply(3));
        var ref3 = new GreaterThan(x, Literal$.MODULE$.apply(0));
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(
                new Filter(ref1, cte), new Filter(ref2, cte), new Filter(ref3, cte))), false, false);
        assertEquals(plan, rule.apply(plan));
    }
}
