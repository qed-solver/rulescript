package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinToSemiJoin(t *testing.T) {
	rule := loadRule(t, "JoinToSemiJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	rAgg := e.f.ConstructGroupBy(r, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])})
	inner := e.f.ConstructInnerJoin(l, rAgg,
		e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	before := e.f.ConstructProject(inner, nil, opt.MakeColSet(lc[0]))
	semi := e.f.ConstructSemiJoin(l, r,
		e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(semi, nil, opt.MakeColSet(lc[0]))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructInnerJoin(l, rAgg,
			e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{}),
		nil, opt.MakeColSet(lc[0])))

	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructLeftJoin(l, rAgg,
			e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{}),
		nil, opt.MakeColSet(lc[0])))

	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructInnerJoin(l, r,
			e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{}),
		nil, opt.MakeColSet(lc[0])))
}
