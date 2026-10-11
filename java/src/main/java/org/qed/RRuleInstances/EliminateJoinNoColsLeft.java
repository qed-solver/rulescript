package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — covers the non-correlated InnerJoin operator; the correlated InnerJoinApply variant of the source rule is not modeled.
public record EliminateJoinNoColsLeft() implements RRule {
    // $left & ColsAreEmpty(OutputCols($left)) & HasOneRow($left): a
    // one-row, zero-column relation (a Values, scalar GroupBy, or other
    // one-row operator whose columns are never used, with all columns pruned).
    static final RelNode oneRow = RuleBuilder.create().values(new String[]{"_r"}, true).build();
    static final RelRN oneRowRN = () -> oneRow;
    static final RelRN left = oneRowRN.project(Seq.<RexRN>empty());
    static final RelRN right = RelRN.scan("R", "R_Type");

    @Override
    public RelRN before() {
        // (one-row zero-column L) INNER JOIN R ON on — since L has no columns,
        // the join condition ranges only over R's columns.
        return left.join(JoinRelType.INNER, left.joinPred("on", right), right);
    }

    @Override
    public RelRN after() {
        // Joining with a one-row zero-column input just filters R by the
        // join condition.
        return right.filter(right.pred("on"));
    }
}
