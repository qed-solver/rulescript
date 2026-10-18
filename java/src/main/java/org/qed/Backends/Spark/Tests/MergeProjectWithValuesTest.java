package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeProjectWithValuesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("MergeProjectWithValues.json"));

    private static LocalRelation oneRow(Attribute c0, Attribute c1) {
        var row = new GenericInternalRow(new Object[]{1, 2});
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(c0, c1)), Catalyst.<InternalRow>seq(List.of(row)),
                false, Match.noStream());
    }

    private static NamedExpression aliasedLiteral(int value, String name) {
        return Alias$.MODULE$.apply(Literal$.MODULE$.apply(value), name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void mergesLiteralProjectionIntoValues() {
        var c0 = Support.attribute("c0", false);
        var c1 = Support.attribute("c1", false);
        var values = oneRow(c0, c1);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(c0, aliasedLiteral(3, "k"))), values);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof LocalRelation);
        assertEquals(2, rewritten.output().size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(List.of("[1,3]"), Support.run(plan));
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectionOfOtherLiteralAlone() {
        var c0 = Support.attribute("c0", false);
        var c1 = Support.attribute("c1", false);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(c0, aliasedLiteral(4, "k"))), oneRow(c0, c1));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectionOverTwoRowsAlone() {
        var c0 = Support.attribute("c0", false);
        var c1 = Support.attribute("c1", false);
        var rows = Catalyst.<InternalRow>seq(List.of(new GenericInternalRow(new Object[]{1, 2}),
                new GenericInternalRow(new Object[]{3, 4})));
        var twoRows = new LocalRelation(Catalyst.<Attribute>seq(List.of(c0, c1)), rows, false, Match.noStream());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(c0, aliasedLiteral(3, "k"))), twoRows);
        assertEquals(plan, rule.apply(plan));
    }
}
