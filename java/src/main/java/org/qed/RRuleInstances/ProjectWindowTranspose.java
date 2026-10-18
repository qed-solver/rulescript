package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the window is modeled as a single partition-by group of per-partition uninterpreted aggregate window functions over one referenced operand column, with exactly one never-referenced passthrough input column and an uninterpreted top projection that ignores it
public record ProjectWindowTranspose() implements RRule {
    // Window input B: three columns — partition key k, operand column v
    // (referenced by the window functions), and never-referenced passthrough
    // column x (the column the rule prunes out).
    static final RelRN base = RelRN.scanMany("B", Seq.of(
            RexRN.varType("K_Type", true),
            RexRN.varType("V_Type", true),
            RexRN.varType("X_Type", true)));

    // The Project the rule inserts below the window: keep only the referred
    // columns (k, v), dropping x.
    static final RelRN needed =
            base.project(Seq.of(base.field(0), base.field(1)));

    // The per-partition window values: two uninterpreted aggregate window
    // functions f(v) and g(v), grouped by the partition key k. Neither
    // references x, so they hold the same values over the pruned input.
    static final RelRN perPart = new RelRN.Aggregate(
            needed,
            Seq.of(needed.field(0)),
            Seq.of(needed.field(1).aggCall("f"), needed.field(1).aggCall("g")));

    // A Window operator's output shape (input cols + window cols) is an
    // INNER equi-join of the window input with the per-partition values on
    // the partition key: each input row matches exactly one window row,
    // preserving all input columns and their multiplicity. joinField ordinals
    // are in the concatenated (left, right) field space.
    static final RexRN condFull = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(base.joinField(0, perPart), base.joinField(3, perPart)));
    static final RelRN winFull = base.join(JoinRelType.INNER, condFull, perPart);

    static final RexRN condPruned = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(needed.joinField(0, perPart), needed.joinField(2, perPart)));
    static final RelRN winPruned = needed.join(JoinRelType.INNER, condPruned, perPart);

    // Shared uninterpreted top-project symbol Top, written over the columns
    // actually referred (k, v, f, g) — the same symbol at the same arity on
    // both sides, encoding the rule's index adjustment (fields 0,1,4,5
    // before the prune, 0,1,3,4 after); it never mentions x.
    static final SqlOperator top = RuleBuilder.create()
            .genericProjectionOp("Top", RexRN.varType("Top_Type", true));

    @Override
    public RelRN before() {
        // Project(Top) over Window(full input (k, v, x)).
        // winFull = (k, v, x, k', f, g); Top picks (k, v, f, g) = 0, 1, 4, 5.
        return winFull.project(new RexRN.Proj(top, Seq.of(
                winFull.field(0), winFull.field(1),
                winFull.field(4), winFull.field(5))));
    }

    @Override
    public RelRN after() {
        // Project(Top) over Window(pruned input (k, v)).
        // winPruned = (k, v, k', f, g); Top picks (k, v, f, g) = 0, 1, 3, 4.
        return winPruned.project(new RexRN.Proj(top, Seq.of(
                winPruned.field(0), winPruned.field(1),
                winPruned.field(3), winPruned.field(4))));
    }
}
