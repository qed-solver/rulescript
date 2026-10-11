package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL -- LEFT join only, single-column L, 2-column selectInput
// (k, v) with the Project passthrough keeping only v before the rewrite and
// (k, v) after (matching UnionCols(passthrough, OutputCols(selectInput))).
// The filter is modeled as uncorrelated (over selectInput's own k column
// only) rather than genuinely referencing the outer L row -- same precedent
// as the already-PROVED TryDecorrelateSelect: the $right-has-outer-cols /
// FiltersBoundBy guard is a firing heuristic, not a soundness precondition,
// since the filter-into-ON merge is valid regardless of what the filter
// actually depends on.
public record TryDecorrelateProjectSelect() implements RRule {
    static final RelType.VarType kType = new RelType.VarType("K", true);
    static final RelType.VarType vType = new RelType.VarType("V", true);
    static final RelRN r = RelRN.scanMany("R", Seq.of(kType, vType));
    static final RelRN left = RelRN.scan("L", "L_Type");

    static final SqlOperator filterOp = RuleBuilder.create().genericPredicateOp("select_filter", true);
    static final SqlOperator onOp = RuleBuilder.create().genericPredicateOp("on_cond", true);

    @Override
    public RelRN before() {
        // Select(R, filter(k))
        RelRN selectRel = r.filter(new RexRN.Pred(filterOp, Seq.of(r.field(0))));
        // Project(Select(...), passthrough = [v]) -- k dropped here.
        RelRN projectRel = new RelRN.ProjectMany(Seq.of(selectRel.field(1)), selectRel);
        // LeftJoinApply(L, Project(...), on(L, v))
        RexRN onCond = new RexRN.Pred(onOp,
                Seq.of(left.joinField(0, projectRel), left.joinField(1, projectRel)));
        return left.join(JoinRelType.LEFT, onCond, projectRel);
    }

    @Override
    public RelRN after() {
        // Project(selectInput, UnionCols(passthrough, OutputCols(selectInput))) = [k, v] here.
        RelRN pushedProject = new RelRN.ProjectMany(Seq.of(r.field(0), r.field(1)), r);
        // ConcatFilters(on, filter): same on(L, v) predicate (same logical
        // argument order as before()), AND the filter, now over k's new
        // position in the join row.
        RexRN onCond2 = new RexRN.Pred(onOp,
                Seq.of(left.joinField(0, pushedProject), left.joinField(2, pushedProject)));
        RexRN filterCond2 = new RexRN.Pred(filterOp, Seq.of(left.joinField(1, pushedProject)));
        RelRN joined = left.join(JoinRelType.LEFT, RexRN.and(onCond2, filterCond2), pushedProject);
        // Project([], OutputCols2(L, right)) -- drop k, restore before()'s [L, v] shape.
        return joined.project(Seq.of(joined.field(0), joined.field(2)));
    }
}
