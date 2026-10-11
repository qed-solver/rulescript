package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestProjectToSemiJoin(t *testing.T) {
	rule := loadRule(t, "ProjectToSemiJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 2)
	r, rc := e.scan("u", 2)
	rAgg := e.f.ConstructGroupBy(r, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[1])})
	inner := e.f.ConstructInnerJoin(l, rAgg,
		e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1]))), &memo.JoinPrivate{})
	before := e.f.ConstructProject(inner, nil, opt.MakeColSet(lc[0], lc[1]))
	semi := e.f.ConstructSemiJoin(l, r,
		e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1]))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(semi, nil, opt.MakeColSet(lc[0], lc[1]))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructInnerJoin(l, rAgg,
			e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[1]))), &memo.JoinPrivate{}),
		nil, opt.MakeColSet(lc[0], lc[1])))

	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructLeftJoin(l, rAgg,
			e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1]))), &memo.JoinPrivate{}),
		nil, opt.MakeColSet(lc[0], lc[1])))

	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructInnerJoin(l, r,
			e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1]))), &memo.JoinPrivate{}),
		nil, opt.MakeColSet(lc[0], lc[1])))
}
