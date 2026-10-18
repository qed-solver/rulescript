package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.If;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushFoldableIntoBranchesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushFoldableIntoBranches.json"));

    private static Expression plusTen(Expression e) {
        return new Add(e, Literal$.MODULE$.apply(10));
    }

    private static Project project(Expression e, LogicalPlan child) {
        var alias = Alias$.MODULE$.apply(e, "out", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
        return new Project(Catalyst.<NamedExpression>seq(List.of(alias)), child);
    }

    private static If ifIn(Expression e) {
        if (e instanceof If i) {
            return i;
        }
        for (var child : Catalyst.<Expression>list(e.children())) {
            var found = ifIn(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static If ifInPlan(LogicalPlan plan) {
        for (var e : Catalyst.<Expression>list(plan.expressions())) {
            var found = ifIn(e);
            if (found != null) {
                return found;
            }
        }
        for (var child : Catalyst.<LogicalPlan>list(plan.children())) {
            var found = ifInPlan(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void pushesUnaryOperatorIntoBothBranches() {
        var base = Support.relation("src", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var cond = new GreaterThan(a, Literal$.MODULE$.apply(1));
        var plan = project(plusTen(new If(cond, a, b)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var ite = ifInPlan(rewritten);
        assertTrue(ite != null, "no If in the rewritten plan:\n" + rewritten);
        assertEquals(cond, ite.predicate());
        assertEquals(plusTen(a), ite.trueValue());
        assertEquals(plusTen(b), ite.falseValue());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAProjectionOverThreeColumnsAlone() {
        var base = Support.relation("src", 3, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var cond = new GreaterThan(a, Literal$.MODULE$.apply(1));
        var plan = project(plusTen(new If(cond, a, b)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnIfWithOperatorOnOneBranchAlone() {
        var base = Support.relation("src", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var cond = new GreaterThan(a, Literal$.MODULE$.apply(1));
        var plan = project(new If(cond, plusTen(a), b), base);
        assertEquals(plan, rule.apply(plan));
    }
}
