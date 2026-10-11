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
	bottomOn := e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0])))
	topOn := e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(cc[0])))
	groups := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(ac[0], cc[0])}

	bottom := e.f.ConstructLeftJoin(a, b, bottomOn, &memo.JoinPrivate{})
	top := e.f.ConstructLeftJoin(bottom, c, topOn, &memo.JoinPrivate{})
	before := e.f.ConstructGroupBy(top, nil, groups)
	want := e.f.ConstructGroupBy(
		e.f.ConstructLeftJoin(a, c, topOn, &memo.JoinPrivate{}),
		nil,
		groups,
	)
	e.expectRewrite(rule, before, want)

	innerTop := e.f.ConstructInnerJoin(bottom, c, topOn, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(innerTop, nil, groups))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(top, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(ac[0]),
	}))

	bTopOn := e.filters(e.f.ConstructEq(e.v(bc[0]), e.v(cc[0])))
	bTop := e.f.ConstructLeftJoin(bottom, c, bTopOn, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(bTop, nil, groups))
}
