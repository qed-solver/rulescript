package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.InSubquery;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyEqualsAnyTupleTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyEqualsAnyTuple.json"));

    @Test
    void semiJoinOnEqualityBecomesInFilter() {
        var s = Support.relation("S", 1, List.of(), new Random(1));
        var v = Support.relation("V", 1, List.of(), new Random(2));
        var sc = Catalyst.<Attribute>list(s.output()).get(0);
        var vc = Catalyst.<Attribute>list(v.output()).get(0);
        var plan = new Join(s, v, LeftSemi$.MODULE$, Catalyst.some(new EqualTo(sc, vc)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == s && f.condition() instanceof InSubquery in
                && Catalyst.<Expression>list(in.values()).size() == 1
                && Catalyst.<Expression>list(in.values()).get(0) instanceof Attribute a && a.exprId().equals(sc.exprId())
                && in.query().plan() instanceof LocalRelation
                && Catalyst.list(in.query().plan().output()).size() == 1);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void antiJoinOnEqualityIsLeftAlone() {
        var s = Support.relation("S", 1, List.of(), new Random(1));
        var v = Support.relation("V", 1, List.of(), new Random(2));
        var sc = Catalyst.<Attribute>list(s.output()).get(0);
        var vc = Catalyst.<Attribute>list(v.output()).get(0);
        var plan = new Join(s, v, LeftAnti$.MODULE$, Catalyst.some(new EqualTo(sc, vc)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void innerJoinOnEqualityIsLeftAlone() {
        var s = Support.relation("S", 1, List.of(), new Random(1));
        var v = Support.relation("V", 1, List.of(), new Random(2));
        var sc = Catalyst.<Attribute>list(s.output()).get(0);
        var vc = Catalyst.<Attribute>list(v.output()).get(0);
        var plan = new Join(s, v, Inner$.MODULE$, Catalyst.some(new EqualTo(sc, vc)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void semiJoinOnInequalityIsLeftAlone() {
        var s = Support.relation("S", 1, List.of(), new Random(1));
        var v = Support.relation("V", 1, List.of(), new Random(2));
        var sc = Catalyst.<Attribute>list(s.output()).get(0);
        var vc = Catalyst.<Attribute>list(v.output()).get(0);
        var plan = new Join(s, v, LeftSemi$.MODULE$, Catalyst.some(new LessThan(sc, vc)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
