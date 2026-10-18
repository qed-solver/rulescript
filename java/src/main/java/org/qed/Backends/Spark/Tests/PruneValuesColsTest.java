package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PruneValuesColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneValuesCols.json"));

    private static LocalRelation literal(List<String> names, int[][] rows) {
        var attrs = new java.util.ArrayList<Attribute>();
        for (var name : names) {
            attrs.add(Support.attribute(name, false));
        }
        var data = new java.util.ArrayList<InternalRow>();
        for (var row : rows) {
            data.add(new GenericInternalRow(Arrays.stream(row).boxed().toArray()));
        }
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(data), false, Match.noStream());
    }

    private static NamedExpression topOf(Expression arg) {
        return Alias$.MODULE$.apply(new IsNull(arg), "top", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static LogicalPlan leaf(LogicalPlan plan) {
        var node = plan;
        while (!(node instanceof LocalRelation)) {
            node = node.children().head();
        }
        return node;
    }

    @Test
    void prunesTheUnusedThirdLiteralColumn() {
        var values = literal(List.of("c0", "c1", "c2"), new int[][]{{1, 2, 3}});
        var out = Catalyst.<Attribute>list(values.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(topOf(out.get(0)), out.get(1))), values);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertInstanceOf(Project.class, rewritten);
        var reduced = assertInstanceOf(LocalRelation.class, leaf(rewritten));
        assertEquals(2, reduced.output().size());
        assertEquals(1, reduced.data().size());
        assertEquals(2, Catalyst.list(rewritten.output()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesTwoRowValuesAlone() {
        var values = literal(List.of("c0", "c1", "c2"), new int[][]{{1, 2, 3}, {4, 5, 6}});
        var out = Catalyst.<Attribute>list(values.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(topOf(out.get(0)), out.get(1))), values);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectionOfThirdColumnAlone() {
        var values = literal(List.of("c0", "c1", "c2"), new int[][]{{1, 2, 3}});
        var out = Catalyst.<Attribute>list(values.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(topOf(out.get(0)), out.get(2))), values);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoColumnValuesAlone() {
        var values = literal(List.of("c0", "c1"), new int[][]{{1, 2}});
        var out = Catalyst.<Attribute>list(values.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(topOf(out.get(0)), out.get(1))), values);
        assertEquals(plan, rule.apply(plan));
    }
}
