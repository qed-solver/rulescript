package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateJoinTranspose(t *testing.T) {
	rule := loadRule(t, "AggregateJoinTranspose")
	e := newEnv(t)
	l, lc := e.scan("t", 2)
	r, rc := e.scan("u", 2)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructGroupBy(
		e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}),
		memo.AggregationsExpr{},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[1], rc[1])},
	)
	leftAgg := e.f.ConstructGroupBy(
		l,
		memo.AggregationsExpr{},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0], lc[1])},
	)
	rightAgg := e.f.ConstructGroupBy(
		r,
		memo.AggregationsExpr{},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0], rc[1])},
	)
	rejoin := e.f.ConstructInnerJoin(leftAgg, rightAgg, on, &memo.JoinPrivate{})
	want := e.f.ConstructGroupBy(
		rejoin,
		memo.AggregationsExpr{},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[1], rc[1])},
	)
	e.expectRewrite(rule, before, want)
	got, ok := rule.Apply(&e.f, before)
	if !ok {
		t.Fatal("AggregateJoinTranspose did not apply")
	}
	gb, ok := got.(*memo.GroupByExpr)
	if !ok {
		t.Fatalf("result is not a GroupBy:\n%s", e.format(got))
	}
	if cols := gb.Private().(*memo.GroupingPrivate).GroupingCols; !cols.Equals(opt.MakeColSet(lc[1], rc[1])) {
		t.Fatalf("top grouping columns are %v, want %v", cols, opt.MakeColSet(lc[1], rc[1]))
	}
	join, ok := gb.Input.(*memo.InnerJoinExpr)
	if !ok {
		t.Fatalf("input of the top GroupBy is not an inner join:\n%s", e.format(gb.Input))
	}
	leftGroups, ok := join.Left.(*memo.GroupByExpr)
	if !ok || !leftGroups.Private().(*memo.GroupingPrivate).GroupingCols.Equals(opt.MakeColSet(lc[0], lc[1])) {
		t.Fatalf("left side is not grouped by the join key and group column:\n%s", e.format(join.Left))
	}
	rightGroups, ok := join.Right.(*memo.GroupByExpr)
	if !ok || !rightGroups.Private().(*memo.GroupingPrivate).GroupingCols.Equals(opt.MakeColSet(rc[0], rc[1])) {
		t.Fatalf("right side is not grouped by the join key and group column:\n%s", e.format(join.Right))
	}

	leftJoinBefore := e.f.ConstructGroupBy(
		e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}),
		memo.AggregationsExpr{},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[1], rc[1])},
	)
	e.expectNoMatch(rule, leftJoinBefore)

	aggCol := e.f.Metadata().AddColumn("max_y", types.Int)
	maxAgg := e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(rc[1])), aggCol)
	withAggBefore := e.f.ConstructGroupBy(
		e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}),
		memo.AggregationsExpr{maxAgg},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[1])},
	)
	e.expectNoMatch(rule, withAggBefore)
}
