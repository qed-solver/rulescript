package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestAggregateJoinRemove(t *testing.T) {
	rule := loadRule(t, "AggregateJoinRemove")
	e := newEnv(t)
	left, lc := e.scan("u", 1)
	right, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	joined := e.f.ConstructLeftJoin(left, right, on, &memo.JoinPrivate{})
	before := e.f.ConstructGroupBy(joined, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	})
	want := e.f.ConstructGroupBy(left, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	})
	e.expectRewrite(rule, before, want)

	inner := e.f.ConstructInnerJoin(left, right, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(inner, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	}))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(joined, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(rc[0]),
	}))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(left, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	}))
}
