package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — left is assumed to already be a single-column strict-key relation (so no ordinality/ConstAgg canary is synthesized) and the right GroupBy has exactly one grouping column and one aggregate over a non-key column
public record TryDecorrelateGroupBy() implements RRule {
  // left: single column with a strict key => EnsureKey(left) is the identity and NonKeyCols(left) is empty
  static final RelRN left = RelRN.scan("L", new RelType.VarType("L_Type", false), true);
  // right GroupBy input: grouping column g, aggregated column a
  static final RelRN right = RelRN.scanMany("R", Seq.of(
      new RelType.VarType("G_Type", false),
      new RelType.VarType("A_Type", false)));
  // GroupBy(R): GROUP BY g, f(a)  -> output (g, f)
  static final RelRN rightGrouped = new RelRN.Aggregate(
      right, Seq.of(right.field(0)), Seq.of(right.field(1).aggCall("f")));

  @Override
  public RelRN before() {
    // InnerJoin(L, GroupBy(R), on); inner join with a condition = cross join + filter
    RelRN j = left.join(JoinRelType.INNER, RexRN.trueLiteral(), rightGrouped); // (l, g, f)
    return j.filter(j.pred("on"));
  }

  @Override
  public RelRN after() {
    // InnerJoinApply(EnsureKey(L), R, TRUE) = cross join; then group by (l, g) with f(a)
    RelRN j = left.join(JoinRelType.INNER, RexRN.trueLiteral(), right); // (l, g, a)
    RelRN g = new RelRN.Aggregate(
        j, Seq.of(j.field(0), j.field(1)), Seq.of(j.field(2).aggCall("f"))); // (l, g, f)
    // Select(on) over the GroupBy output; no canary/ordinality columns to project away
    return g.filter(g.pred("on"));
  }
}