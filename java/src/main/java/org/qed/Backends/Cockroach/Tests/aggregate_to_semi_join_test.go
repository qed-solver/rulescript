package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestAggregateToSemiJoin(t *testing.T) {
	rule := loadRule(t, "AggregateToSemiJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	rAgg := e.f.ConstructGroupBy(r, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(rc[0]),
	})
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	join := e.f.ConstructInnerJoin(l, rAgg, on, &memo.JoinPrivate{})
	before := e.f.ConstructGroupBy(join, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	})
	semi := e.f.ConstructSemiJoin(l, r, on, &memo.JoinPrivate{})
	want := e.f.ConstructGroupBy(semi, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	})
	e.expectRewrite(rule, before, want)

	leftJoin := e.f.ConstructLeftJoin(l, rAgg, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(leftJoin, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	}))

	lt := e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0])))
	ltJoin := e.f.ConstructInnerJoin(l, rAgg, lt, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(ltJoin, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	}))

	rawJoin := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(rawJoin, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	}))
}
