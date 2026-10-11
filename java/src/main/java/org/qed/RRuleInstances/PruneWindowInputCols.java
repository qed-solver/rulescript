package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — one window function, modeled as a per-partition uninterpreted aggregate whose input is the pruned (needed-columns-only) relation in both sides, so the never-used passthrough column is provably absent from the window's aggregate input (encoding the NeededWindowCols precondition by construction).
public record PruneWindowInputCols() implements RRule {
    static final RelType.VarType kT = RexRN.varType("k", false);
    static final RelType.VarType vT = RexRN.varType("v", false);
    static final RelType.VarType xT = RexRN.varType("x", false);

    // Window's full input: rows (k, v, x). k is the partition column, v is
    // needed by the window function, x is the never-used passthrough column
    // the rule prunes.
    static final RelRN source = RelRN.scanMany("Input", Seq.of(kT, vT, xT));

    // (PruneCols $innerInput $needed): the window input rewritten to output
    // only the needed columns (k, v) — x dropped.
    static final RelRN pruned =
            source.project(Seq.of(source.field(0), source.field(1)));

    // The window function, satisfying the NeededWindowCols precondition by
    // construction: its value is functionally determined by the partition
    // column k and is an uninterpreted aggregate computed over the *needed*
    // columns only (grouped by k, over v) — it never references the pruned
    // column x. Built once and shared by both sides, so both sides use the
    // identical per-partition window value.
    static final RelRN perPart = new RelRN.Aggregate(
            pruned,
            Seq.of(pruned.field(0)),
            Seq.of(pruned.field(1).aggCall("w")));

    // A Window operator's output shape (input cols + window cols) is an
    // INNER equi-join of the window input with the per-partition values on
    // the partition column k: each input row matches exactly one window row,
    // preserving all input columns and their multiplicity. joinField ordinals
    // are in the concatenated (left, right) field space.
    static final RexRN condFull = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(source.joinField(0, perPart), source.joinField(3, perPart)));
    static final RelRN windowFull = source.join(JoinRelType.INNER, condFull, perPart);

    static final RexRN condPruned = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(pruned.joinField(0, perPart), pruned.joinField(2, perPart)));
    static final RelRN windowPruned = pruned.join(JoinRelType.INNER, condPruned, perPart);

    // Shared uninterpreted outer projection symbol Top, written only over the
    // needed output columns (k, v, w) so the same symbol applies on both sides
    // at the same arity, ignoring the pruned column x.
    static final SqlOperator top = RuleBuilder.create()
            .genericProjectionOp("Top", new RelType.VarType("Top_Type", true));

    @Override
    public RelRN before() {
        // Project(Top) over Window(full input (k, v, x)).
        // windowFull = (k, v, x, k', w); Top picks (k, v, w) = fields 0, 1, 4.
        return windowFull.project(new RexRN.Proj(top, Seq.of(
                windowFull.field(0), windowFull.field(1), windowFull.field(4))));
    }

    @Override
    public RelRN after() {
        // Project(Top) over Window(pruned input (k, v)).
        // windowPruned = (k, v, k', w); Top picks (k, v, w) = fields 0, 1, 3.
        return windowPruned.project(new RexRN.Proj(top, Seq.of(
                windowPruned.field(0), windowPruned.field(1), windowPruned.field(3))));
    }
}
