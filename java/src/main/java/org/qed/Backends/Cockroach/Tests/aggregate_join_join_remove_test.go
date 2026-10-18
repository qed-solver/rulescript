package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestAggregateJoinJoinRemove(t *testing.T) {
	rule := loadRule(t, "AggregateJoinJoinRemove")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("nn", 1)
	onAB := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))) }
	onAC := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))) }
	group := func(input memo.RelExpr, cols ...opt.ColumnID) memo.RelExpr {
		return e.f.ConstructGroupBy(input, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{
			GroupingCols: opt.MakeColSet(cols...),
		})
	}

	bottom := e.f.ConstructLeftJoin(a, b, onAB(), &memo.JoinPrivate{})
	top := e.f.ConstructLeftJoin(bottom, c, onAC(), &memo.JoinPrivate{})
	before := group(top, ac[0], cc[0])
	after := group(e.f.ConstructLeftJoin(a, c, onAC(), &memo.JoinPrivate{}), ac[0], cc[0])
	e.expectRewrite(rule, before, after)

	innerBottom := e.f.ConstructInnerJoin(a, b, onAB(), &memo.JoinPrivate{})
	e.expectNoMatch(rule, group(e.f.ConstructLeftJoin(innerBottom, c, onAC(), &memo.JoinPrivate{}), ac[0], cc[0]))

	e.expectNoMatch(rule, group(e.f.ConstructLeftJoin(bottom, c, onAC(), &memo.JoinPrivate{}), bc[0], cc[0]))
}
