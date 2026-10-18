package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
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

class RightAssociateJoinsRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RightAssociateJoinsRight.json"));

    @Test
    void reassociatesInnerJoinWithOnTrueOnTheRight() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var ta = Catalyst.<Attribute>list(a.output()).get(0);
        var tb = Catalyst.<Attribute>list(b.output()).get(0);
        var tc = Catalyst.<Attribute>list(c.output()).get(0);
        var bc = new Join(b, c, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var cond = new And(new EqualTo(ta, tc), new LessThan(ta, tb));
        var plan = new Join(a, bc, Inner$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var project = assertInstanceOf(Project.class, rewritten);
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(project.projectList()));
        var outer = assertInstanceOf(Join.class, project.child());
        var inner = assertInstanceOf(Join.class, outer.left());
        assertSame(a, inner.left());
        assertSame(c, inner.right());
        assertSame(b, outer.right());
        assertEquals(Option.<Expression>apply(new EqualTo(ta, tc)), inner.condition());
        assertEquals(Option.<Expression>apply(new LessThan(ta, tb)), outer.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterOuterJoinAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var ta = Catalyst.<Attribute>list(a.output()).get(0);
        var tb = Catalyst.<Attribute>list(b.output()).get(0);
        var tc = Catalyst.<Attribute>list(c.output()).get(0);
        var bc = new Join(b, c, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var cond = new And(new EqualTo(ta, tc), new LessThan(ta, tb));
        var plan = new Join(a, bc, LeftOuter$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());

        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnInnerRightJoinWithAConditionAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var ta = Catalyst.<Attribute>list(a.output()).get(0);
        var tb = Catalyst.<Attribute>list(b.output()).get(0);
        var tc = Catalyst.<Attribute>list(c.output()).get(0);
        var bc = new Join(b, c, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(tb, tc)), JoinHint$.MODULE$.NONE());
        var cond = new And(new EqualTo(ta, tc), new LessThan(ta, tb));
        var plan = new Join(a, bc, Inner$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());

        assertEquals(plan, rule.apply(plan));
    }
}
