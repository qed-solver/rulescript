package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PruneExplainColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneExplainCols.json"));

    private static NamedExpression alias(Expression value) {
        return Alias$.MODULE$.apply(value, "e", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression plusOne(Attribute a) {
        return Add$.MODULE$.apply(a, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
    }

    private static Expression sumOfTwo(Attribute a, Attribute b) {
        return Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3());
    }

    @Test
    void prunesTheColumnTheExplainDoesNotNeed() {
        var base = Support.relation("I", 2, List.of(), new Random(1));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(alias(plusOne(columns.get(0))))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(Project.class, rewritten);
        var inner = assertInstanceOf(Project.class, ((Project) rewritten).child());
        assertEquals(List.of(columns.get(0)), Catalyst.list(inner.projectList()));
        assertSame(base, inner.child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnExplainThatReadsThePrunedColumnAlone() {
        var base = Support.relation("I", 2, List.of(), new Random(1));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(alias(plusOne(columns.get(1))))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnExplainThatReadsBothColumnsAlone() {
        var base = Support.relation("I", 2, List.of(), new Random(1));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(alias(sumOfTwo(columns.get(0), columns.get(1))))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnExplainOverThreeColumnsAlone() {
        var base = Support.relation("I", 3, List.of(), new Random(1));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(alias(plusOne(columns.get(0))))), base);
        assertEquals(plan, rule.apply(plan));
    }
}
