package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestEliminateJoinUnderGroupByRight(t *testing.T) {
	rule := loadRule(t, "EliminateJoinUnderGroupByRight")
	e := newEnv(t)
	l, lc := e.scan("nn", 1)
	r, rc := e.scan("nn", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	grouping := func(col opt.ColumnID) *memo.GroupingPrivate {
		return &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(col)}
	}
	before := e.f.ConstructGroupBy(
		e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}),
		memo.EmptyAggregationsExpr,
		grouping(rc[0]),
	)
	want := e.f.ConstructGroupBy(r, memo.EmptyAggregationsExpr, grouping(rc[0]))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}),
		memo.EmptyAggregationsExpr,
		grouping(rc[0]),
	))
}
