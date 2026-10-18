package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — fixed-shape single-foldable case: 1-column scan, Project([x, F]) with F a 0-arity (foldable) uninterpreted expression, and a parent Filter predicate referencing the alias column; models only the core constant-substitution step (no Aggregate/Join/Expand/streaming propagation).
public record FoldablePropagation() implements RRule {
    static final RelRN input = RelRN.scan("S", "S_Type");
    static final RexRN x = input.field(0);
    static final SqlOperator fOp =
            RuleBuilder.create().genericProjectionOp("F", new RelType.VarType("F_Type", true));
    // 0-arity uninterpreted projection: independent of the input, i.e. a foldable constant.
    static final RexRN fConst = new RexRN.Proj(fOp, Seq.<RexRN>of());
    // The Project stays below the filter; its output is [x, F] where F is the foldable.
    static final RelRN proj = input.project(Seq.of(x, fConst));
    static final SqlOperator pOp =
            RuleBuilder.create().genericPredicateOp("P", true);

    @Override
    public RelRN before() {
        // Filter(P(alias, x), Project([x, F], S)): the parent references the foldable's alias column (field 1).
        return proj.filter(new RexRN.Pred(pOp, Seq.of(proj.field(1), proj.field(0))));
    }

    @Override
    public RelRN after() {
        // Filter(P(F, x), Project([x, F], S)): the alias reference is replaced in place by the foldable expression.
        return proj.filter(new RexRN.Pred(pOp, Seq.of(fConst, proj.field(0))));
    }
}
