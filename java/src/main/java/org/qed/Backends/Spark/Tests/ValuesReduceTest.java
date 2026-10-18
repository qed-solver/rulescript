package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ValuesReduceTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ValuesReduce.json"));

    private static LocalRelation input(int lastOfSecondRow) {
        var a = Support.attribute("a", false);
        var b = Support.attribute("b", false);
        var c = Support.attribute("c", false);
        var rows = Catalyst.<InternalRow>seq(List.of(
                new GenericInternalRow(new Object[]{1, 2, 3}),
                new GenericInternalRow(new Object[]{4, 5, lastOfSecondRow})));
        return new LocalRelation(Catalyst.seq(List.of(a, b, c)), rows, false, Match.noStream());
    }

    @Test
    void foldsColumnSelectionOfValuesIntoSmallerValues() {
        var base = input(6);
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(2), cols.get(0))), base);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var project = assertInstanceOf(Project.class, rewritten);
        var outputs = Catalyst.<Attribute>list(project.output());
        assertEquals(List.of("c", "a"), outputs.stream().map(Attribute::name).toList());
        assertEquals(cols.get(2).exprId(), outputs.get(0).exprId());
        assertEquals(cols.get(0).exprId(), outputs.get(1).exprId());
        var values = assertInstanceOf(LocalRelation.class, project.child());
        var data = Catalyst.<InternalRow>list(values.data());
        assertEquals(2, data.size());
        assertEquals(List.of(3, 1), List.of(data.get(0).getInt(0), data.get(0).getInt(1)));
        assertEquals(List.of(6, 4), List.of(data.get(1).getInt(0), data.get(1).getInt(1)));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectionWithLiteralAlone() {
        var base = input(6);
        var cols = Catalyst.<Attribute>list(base.output());
        var constant = Alias$.MODULE$.apply(Literal$.MODULE$.apply(5), "k", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(constant, cols.get(0))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectionOverFilteredValuesAlone() {
        var base = input(6);
        var cols = Catalyst.<Attribute>list(base.output());
        var filtered = new Filter(new GreaterThan(cols.get(0), Literal$.MODULE$.apply(0)), base);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(2), cols.get(0))), filtered);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectionOverDifferentValuesAlone() {
        var base = input(7);
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(2), cols.get(0))), base);
        assertEquals(plan, rule.apply(plan));
    }
}
