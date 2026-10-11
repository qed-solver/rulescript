package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL -- the "left already has a strict key" branch (EnsureKey is a
// no-op), uncorrelated Input (the HasOuterCols guard is a firing heuristic,
// same precedent as TryDecorrelateSelect/TryDecorrelateGroupBy), Window
// modeled via the join-back-on-partition-key idiom used by
// PushSelectIntoWindow (no frame/ordering semantics, an uninterpreted
// aggregate standing in for the window function), starting with EMPTY
// pre-existing partition columns (partcols=[]) so AddColsToPartition adds
// exactly L's own key as the sole partition column, single aggregate call,
// ON references only the input's value column.
public record TryDecorrelateWindow() implements RRule {
    static final RelType.VarType vType = new RelType.VarType("V", false);
    static final RelRN input = RelRN.scan("Input", vType, false);
    static final RelType.VarType lType = new RelType.VarType("L_Type", false);
    static final RelRN left = RelRN.scan("L", lType, true);

    static final SqlOperator onOp = RuleBuilder.create().genericPredicateOp("on_cond", true);

    @Override
    public RelRN before() {
        // Window(Input, partcols=[]) -- a single scalar (whole-input) window
        // aggregate, modeled per PushSelectIntoWindow as GroupBy(groupSet=[])
        // joined back to every input row via a cross join.
        RelRN perPart = new RelRN.Aggregate(input, Seq.empty(), Seq.of(input.field(0).aggCall("w")));
        RelRN window = input.join(JoinRelType.INNER, RexRN.trueLiteral(), perPart);
        // window fields: 0=Input.v, 1=w
        // InnerJoinApply(L, window, on(L, v))
        RexRN onCond = new RexRN.Pred(onOp, Seq.of(left.joinField(0, window), left.joinField(1, window)));
        return left.join(JoinRelType.INNER, onCond, window);
        // fields: 0=L, 1=v, 2=w
    }

    @Override
    public RelRN after() {
        // InnerJoinApply(L, Input, []) modeled as an uncorrelated InnerJoin on
        // TRUE, matching TryDecorrelateGroupBy/TryDecorrelateSelect's
        // precedent that the outer-cols guard is a firing heuristic, not a
        // soundness precondition.
        RelRN cross = left.join(JoinRelType.INNER, RexRN.trueLiteral(), input);
        // cross fields: 0=L, 1=v
        // Window(cross, partcols=AddColsToPartition([], KeyCols(newLeft))=[L])
        // -- one partition per L row, each partition's window value is the
        // aggregate over exactly that L row's Input rows.
        RelRN augPerPart = new RelRN.Aggregate(cross, Seq.of(cross.field(0)), Seq.of(cross.field(1).aggCall("w")));
        RexRN augCond = new RexRN.Pred(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                Seq.of(cross.joinField(0, augPerPart), cross.joinField(2, augPerPart)));
        RelRN augWindow = cross.join(JoinRelType.INNER, augCond, augPerPart);
        // augWindow fields: 0=L, 1=v, 2=L'(dup partition key), 3=w
        // on(L, v), re-expressed over augWindow's own output columns, applied
        // post-window as the Select wrapper.
        RexRN onCondPost = new RexRN.Pred(onOp, Seq.of(augWindow.field(0), augWindow.field(1)));
        RelRN selected = augWindow.filter(onCondPost);
        // Project back to before()'s column order (L, v, w) -- the duplicated
        // partition-key column (index 2) is dropped, matching OutputCols2.
        return selected.project(Seq.of(selected.field(0), selected.field(1), selected.field(3)));
    }
}
