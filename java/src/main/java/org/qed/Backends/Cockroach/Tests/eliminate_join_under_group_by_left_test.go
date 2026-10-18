package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestEliminateJoinUnderGroupByLeft(t *testing.T) {
	rule := loadRule(t, "EliminateJoinUnderGroupByLeft")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructGroupBy(
		e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}),
		memo.AggregationsExpr{},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])},
	)
	want := e.f.ConstructGroupBy(
		l,
		memo.AggregationsExpr{},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])},
	)
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}),
		memo.AggregationsExpr{},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])},
	))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(
		e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}),
		memo.AggregationsExpr{},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])},
	))
}
