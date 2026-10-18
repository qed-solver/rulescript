package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Multiply$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Subtract$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizeUpdateFieldsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("OptimizeUpdateFields.json"));

    private static NamedExpression named(org.apache.spark.sql.catalyst.expressions.Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static LogicalPlan project(LogicalPlan input, NamedExpression... items) {
        return new Project(Catalyst.seq(List.of(items)), input);
    }

    private static List<Attribute> cols(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output());
    }

    @Test
    void rewritesThreeWithFieldsIntoTwoFinalValues() {
        var base = Support.relation("T", 2, List.of(), new Random(1));
        var in = cols(base);
        var a = in.get(0);
        var b = in.get(1);
        var e1 = named(Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3()), "e1");
        var e2 = named(Subtract$.MODULE$.apply(b, a, Subtract$.MODULE$.apply$default$3()), "e2");
        var e3 = named(Multiply$.MODULE$.apply(a, b, Multiply$.MODULE$.apply$default$3()), "e3");

        var s1 = project(base, a, b, e1, b);
        var c1 = cols(s1);
        var s2 = project(s1, c1.get(0), c1.get(1), c1.get(2), e2);
        var c2 = cols(s2);
        var s3 = project(s2, c2.get(0), c2.get(1), e3, c2.get(3));
        var c3 = cols(s3);
        var plan = project(s3, c3.get(2), c3.get(3));

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project outer && outer.child() instanceof Project middle
                && middle.child() instanceof Project inner && inner.child() == base);
        assertEquals(2, rewritten.output().size());
        assertFalse(rewritten.toString().contains("e1"));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesTheAlreadyRewrittenShapeAlone() {
        var base = Support.relation("T", 2, List.of(), new Random(1));
        var in = cols(base);
        var a = in.get(0);
        var b = in.get(1);
        var e2 = named(Subtract$.MODULE$.apply(b, a, Subtract$.MODULE$.apply$default$3()), "e2");
        var e3 = named(Multiply$.MODULE$.apply(a, b, Multiply$.MODULE$.apply$default$3()), "e3");

        var t1 = project(base, a, b, e3, b);
        var c1 = cols(t1);
        var t2 = project(t1, c1.get(0), c1.get(1), c1.get(2), e2);
        var c2 = cols(t2);
        var plan = project(t2, c2.get(2), c2.get(3));

        assertEquals(plan, rule.apply(plan));
    }
}
