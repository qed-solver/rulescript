package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Except;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EliminateSetLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateSetLeft.json"));

    private static Project reordered(LogicalPlan child) {
        var cols = Catalyst.<Attribute>list(child.output());
        return new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(2), cols.get(0), cols.get(1))), child);
    }

    private static LocalRelation noRows(int arity) {
        var attrs = new ArrayList<Attribute>();
        for (int i = 0; i < arity; i++) {
            attrs.add(Support.attribute("e" + i, true));
        }
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    @Test
    void unionAllWithEmptyRightBecomesLeftReordered() {
        var left = Support.relation("l", 3, List.of(), new Random(1));
        var leftProject = reordered(left);
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(leftProject, noRows(3))), false, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(Project.class, rewritten);
        var project = (Project) rewritten;
        assertSame(left, project.child());
        assertEquals(Catalyst.<NamedExpression>list(leftProject.projectList()),
                Catalyst.<NamedExpression>list(project.projectList()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void unionAllWithNonEmptyRightIsLeftAlone() {
        var left = Support.relation("l", 3, List.of(), new Random(1));
        var right = Support.relation("r", 3, List.of(), new Random(2));
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(reordered(left), right)), false, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void exceptWithEmptyRightIsLeftAlone() {
        var left = Support.relation("l", 3, List.of(), new Random(1));
        var plan = new Except(reordered(left), noRows(3), true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void unionAllWithIdentityLeftProjectIsLeftAlone() {
        var left = Support.relation("l", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(left.output());
        var identity = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(0), cols.get(1), cols.get(2))), left);
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(identity, noRows(3))), false, false);
        assertEquals(plan, rule.apply(plan));
    }
}
