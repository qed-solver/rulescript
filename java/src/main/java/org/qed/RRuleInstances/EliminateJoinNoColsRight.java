package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — covers the non-correlated InnerJoin operator; the correlated InnerJoinApply variant of the source rule is not modeled.
public record EliminateJoinNoColsRight() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    // $right & ColsAreEmpty(OutputCols($right)) & HasOneRow($right): a
    // one-row, zero-column relation (a Values, scalar GroupBy, or other
    // one-row operator whose columns are never used, with all columns pruned).
    static final RelNode oneRow = RuleBuilder.create().values(new String[]{"_r"}, true).build();
    static final RelRN oneRowRN = () -> oneRow;
    static final RelRN right = oneRowRN.project(Seq.<RexRN>empty());

    @Override
    public RelRN before() {
        // L INNER JOIN (one-row zero-column R) ON on  — since R has no columns,
        // the join condition ranges only over L's columns.
        return left.join(JoinRelType.INNER, left.joinPred("on", right), right);
    }

    @Override
    public RelRN after() {
        // Joining with a one-row zero-column input just filters L by the
        // join condition.
        return left.filter(left.pred("on"));
    }
}
