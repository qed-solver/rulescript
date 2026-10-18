package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the uncorrelated single-column scalar-subquery branch is encoded, as the inner cross-join of the outer relation with a concrete one-row constant-valued VALUES relation inlined as that constant in the outer project
public record OptimizeOneRowRelationSubquery() implements RRule {
    // Outer relation L with one column (the subquery's enclosing context).
    static final RelRN left = RelRN.scan("L", "L_Type");

    // The one-row subquery: a single-column constant-valued one-row relation
    // (VALUES (true)), as the RA expansion of a ScalarSubquery over a OneRowRelation.
    static final RelNode dummyNode = RuleBuilder.create()
            .values(new String[]{"d"}, true)
            .build();
    static final RelRN dummy = () -> dummyNode;

    @Override
    public RelRN before() {
        // Project( [l, d], InnerJoin(L, true, VALUES(true))) — the scalar-subquery join form.
        RelRN joined = left.join(JoinRelType.INNER, RexRN.trueLiteral(), dummy);
        return joined.project(Seq.of(joined.field(0), joined.field(1)));
    }

    public RelRN after() {
        // Project( [l, true], L) — the one-row relation's value inlined as a constant.
        return left.project(Seq.of(left.field(0), RexRN.trueLiteral()));
    }
}
