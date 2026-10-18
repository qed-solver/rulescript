package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnionPullUpConstantsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("UnionPullUpConstants.json"));

    private static Expression flag(boolean value) {
        return Literal$.MODULE$.apply(value);
    }

    private static LogicalPlan branch(LogicalPlan child, Expression constant, int column) {
        var attr = Catalyst.<Attribute>list(child.output()).get(column);
        var alias = Alias$.MODULE$.apply((Expression) constant, "flag", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
        return new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(alias, attr)), child);
    }

    private static Union union(LogicalPlan... inputs) {
        return new Union(Catalyst.<LogicalPlan>seq(List.of(inputs)), false, false);
    }

    @Test
    void pullsTheConstantColumnOutOfUnionAll() {
        var a = Support.relation("a", 2, List.of(), new Random(3));
        var b = Support.relation("b", 2, List.of(), new Random(4));
        var plan = union(branch(a, flag(true), 1), branch(b, flag(true), 1));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(Project.class, rewritten);
        var top = (Project) rewritten;
        assertEquals(2, top.projectList().size());
        assertInstanceOf(Project.class, top.child());
        var constantProject = (Project) top.child();
        assertEquals(2, constantProject.projectList().size());
        assertInstanceOf(Union.class, constantProject.child());
        var inner = (Union) constantProject.child();
        var children = Catalyst.<LogicalPlan>list(inner.children());
        assertEquals(2, children.size());
        for (var child : children) {
            assertInstanceOf(Project.class, child);
            assertEquals(1, ((Project) child).projectList().size());
        }
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesUnionWithDifferentConstantAlone() {
        var a = Support.relation("a", 2, List.of(), new Random(3));
        var b = Support.relation("b", 2, List.of(), new Random(4));
        var plan = union(branch(a, flag(true), 1), branch(b, flag(false), 1));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesUnionOfWrongColumnAlone() {
        var a = Support.relation("a", 2, List.of(), new Random(3));
        var b = Support.relation("b", 2, List.of(), new Random(4));
        var plan = union(branch(a, flag(true), 0), branch(b, flag(true), 1));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesUnionOfThreeInputsAlone() {
        var a = Support.relation("a", 2, List.of(), new Random(3));
        var b = Support.relation("b", 2, List.of(), new Random(4));
        var c = Support.relation("c", 2, List.of(), new Random(5));
        var plan = union(branch(a, flag(true), 1), branch(b, flag(true), 1), branch(c, flag(true), 1));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesSingleProjectAlone() {
        var a = Support.relation("a", 2, List.of(), new Random(3));
        var plan = branch(a, flag(true), 1);
        assertEquals(plan, rule.apply(plan));
        assertTrue(rule.apply(plan) instanceof Project);
    }
}
