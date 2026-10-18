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

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PruneMutationInputColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneMutationInputCols.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression plusOne(Expression x) {
        return Add$.MODULE$.apply(x, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
    }

    private static Expression plus(Expression x, Expression y) {
        return Add$.MODULE$.apply(x, y, Add$.MODULE$.apply$default$3());
    }

    @Test
    void prunesInputToTheNeededColumnAndRewritesTheMutation() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var columns = Catalyst.<Attribute>list(base.output());
        var item = alias(plusOne(columns.get(0)), "m");
        var plan = new Project(Catalyst.seq(List.of(item)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(plan.output(), rewritten.output());
        var outer = (Project) rewritten;
        var inner = (Project) outer.child();
        assertEquals(base, inner.child());
        var innerItems = Catalyst.<NamedExpression>list(inner.projectList());
        assertEquals(List.of(columns.get(0)), innerItems);
        var outerItem = Catalyst.<NamedExpression>list(outer.projectList()).get(0);
        assertEquals("m", outerItem.name());
        var reads = Catalyst.<Attribute>list(((Expression) outerItem).references().toSeq());
        assertEquals(List.of(columns.get(0).exprId()), reads.stream().map(Attribute::exprId).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void doesNotFireOnAThreeColumnInput() {
        var base = Support.relation("t", 3, List.of(), new Random(2));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.of(alias(plusOne(columns.get(0)), "m"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void doesNotFireWhenTheMutationReadsTheOtherColumn() {
        var base = Support.relation("t", 2, List.of(), new Random(3));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.of(alias(plusOne(columns.get(1)), "m"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void doesNotFireWhenTheMutationUsesBothColumns() {
        var base = Support.relation("t", 2, List.of(), new Random(4));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.of(alias(plus(columns.get(0), columns.get(1)), "m"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void doesNotFireWhenTheProjectHasAnotherItem() {
        var base = Support.relation("t", 2, List.of(), new Random(5));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(alias(plusOne(columns.get(0)), "m"), columns.get(1))), base);
        assertEquals(plan, rule.apply(plan));
    }
}
