package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
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

class ReorderJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ReorderJoin.json"));

    private static Join join(LogicalPlan left, LogicalPlan right, Option<Expression> cond) {
        return new Join(left, right, Inner$.MODULE$, cond, JoinHint$.MODULE$.NONE());
    }

    private static Expression pAB(Attribute a, Attribute b) {
        return new EqualTo(a, b);
    }

    private static Expression pCD(Attribute d, Attribute c) {
        return new EqualTo(d, c);
    }

    private static Expression qDBC(Attribute d, Attribute b, Attribute c) {
        return new EqualTo(Add$.MODULE$.apply(d, b, Add$.MODULE$.apply$default$3()), c);
    }

    private static Attribute column(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output()).get(0);
    }

    @Test
    void reordersLeftDeepInnerJoinsAndRestoresColumnOrder() {
        var a = Support.relation("A", 1, List.of(0), new Random(1));
        var d = Support.relation("D", 1, List.of(0), new Random(2));
        var c = Support.relation("C", 1, List.of(0), new Random(3));
        var b = Support.relation("B", 1, List.of(0), new Random(4));
        var aA = column(a);
        var dD = column(d);
        var cC = column(c);
        var bB = column(b);

        var aD = join(a, d, Option.<Expression>empty());
        var aDC = join(aD, c, Catalyst.<Expression>some(pCD(dD, cC)));
        var topCond = Catalyst.<Expression>some(Catalyst.conjunction(List.of(pAB(aA, bB), qDBC(dD, bB, cC))));
        var plan = join(aDC, b, topCond);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);

        var project = assertInstanceOf(Project.class, rewritten);
        var top = assertInstanceOf(Join.class, project.child());
        assertSame(c, top.right());
        var middle = assertInstanceOf(Join.class, top.left());
        assertSame(d, middle.right());
        var lower = assertInstanceOf(Join.class, middle.left());
        assertSame(a, lower.left());
        assertSame(b, lower.right());
        assertEquals(List.of(aA, dD, cC, bB), Catalyst.list(project.output()));
        assumeRunnable();
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAloneAtTheTop() {
        var a = Support.relation("A", 1, List.of(0), new Random(1));
        var d = Support.relation("D", 1, List.of(0), new Random(2));
        var c = Support.relation("C", 1, List.of(0), new Random(3));
        var b = Support.relation("B", 1, List.of(0), new Random(4));
        var aA = column(a);
        var dD = column(d);
        var cC = column(c);
        var bB = column(b);

        var aD = join(a, d, Option.<Expression>empty());
        var aDC = join(aD, c, Catalyst.<Expression>some(pCD(dD, cC)));
        var topCond = Catalyst.<Expression>some(Catalyst.conjunction(List.of(pAB(aA, bB), qDBC(dD, bB, cC))));
        var plan = new Join(aDC, b, LeftOuter$.MODULE$, topCond, JoinHint$.MODULE$.NONE());

        assertEquals(plan, rule.apply(plan));
    }

    private static void assumeRunnable() {
        Assumptions.assumeTrue(Support.canExecute());
    }
}
