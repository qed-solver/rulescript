package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Cast$;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.types.LongType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushAssignmentCastsIntoValuesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushAssignmentCastsIntoValues.json"));

    private static NamedExpression alias(Expression child, String name) {
        return Alias$.MODULE$.apply(child, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void pushesTheCastIntoTheValuesRows() {
        var values = Support.relation("v", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(values.output()).get(0);
        var cast = Cast$.MODULE$.apply(x, LongType$.MODULE$, false);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(cast, "c"), alias(Literal$.MODULE$.TrueLiteral(), "t"))), values);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project top && top.projectList().size() == 2
                && top.child() instanceof Project middle && middle.projectList().size() == 2
                && middle.child() instanceof Project inner && inner.projectList().size() == 1 && inner.child() == values);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAProjectWithANonConstantSecondColumnAlone() {
        var values = Support.relation("v", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(values.output()).get(0);
        var cast = Cast$.MODULE$.apply(x, LongType$.MODULE$, false);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(cast, "c"), alias(x, "u"))), values);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectOverAWiderRelationAlone() {
        var values = Support.relation("v", 2, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(values.output()).get(0);
        var cast = Cast$.MODULE$.apply(x, LongType$.MODULE$, false);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(cast, "c"), alias(Literal$.MODULE$.TrueLiteral(), "t"))), values);
        assertEquals(plan, rule.apply(plan));
    }
}
