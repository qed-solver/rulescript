package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertToLocalRelationTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ConvertToLocalRelation.json"));

    private static LocalRelation threeByTwo(List<Attribute> attrs) {
        var rows = List.<InternalRow>of(
                new GenericInternalRow(new Object[]{1, 2, 3}),
                new GenericInternalRow(new Object[]{4, 5, 6}));
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.seq(rows), false, Match.noStream());
    }

    @Test
    void projectsColumnsOfLiteralValues() {
        var attrs = List.of(Support.attribute("a", false), Support.attribute("b", false), Support.attribute("c", false));
        var relation = threeByTwo(attrs);
        var plan = new Project(Catalyst.seq(List.of(attrs.get(2), attrs.get(0))), relation);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(List.of("c", "a"), Catalyst.list(rewritten.output()).stream().map(x -> ((Attribute) x).name()).toList());
        var local = rewritten instanceof Project wrapper ? wrapper.child() : rewritten;
        assertTrue(local instanceof LocalRelation);
        assertEquals(2, Catalyst.list(((LocalRelation) local).output()).size());
        var rows = Catalyst.<InternalRow>list(((LocalRelation) local).data());
        assertEquals(2, rows.size());
        assertEquals(3, rows.get(0).getInt(0));
        assertEquals(1, rows.get(0).getInt(1));
        assertEquals(6, rows.get(1).getInt(0));
        assertEquals(4, rows.get(1).getInt(1));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectionOfOneColumnAlone() {
        var attrs = List.of(Support.attribute("a", false), Support.attribute("b", false), Support.attribute("c", false));
        var plan = new Project(Catalyst.seq(List.of(attrs.get(2))), threeByTwo(attrs));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterOverLiteralValuesAlone() {
        var attrs = List.of(Support.attribute("a", false), Support.attribute("b", false), Support.attribute("c", false));
        var plan = new Filter(new GreaterThan(attrs.get(0), Literal$.MODULE$.apply(0)), threeByTwo(attrs));
        assertEquals(plan, rule.apply(plan));
    }
}
