package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFilterMultiJoinMerge(t *testing.T) {
	rule := loadRule(t, "FilterMultiJoinMerge")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("nn", 1)
	ab := e.f.ConstructInnerJoin(a, b, nil, &memo.JoinPrivate{})
	postJoin := e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))
	cond := e.f.ConstructEq(e.v(bc[0]), e.num(1))
	abc := e.f.ConstructInnerJoin(ab, c, e.filters(postJoin), &memo.JoinPrivate{})
	before := e.f.ConstructSelect(abc, e.filters(cond))
	want := e.f.ConstructInnerJoin(ab, c, e.filters(postJoin, cond), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	left := e.f.ConstructLeftJoin(ab, c, e.filters(postJoin), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(left, e.filters(cond)))

	nonTrueAB := e.f.ConstructInnerJoin(a, b, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructInnerJoin(nonTrueAB, c, e.filters(postJoin), &memo.JoinPrivate{}), e.filters(cond)))

	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructInnerJoin(a, c, e.filters(postJoin), &memo.JoinPrivate{}), e.filters(cond)))

	e.expectNoMatch(rule, abc)
}
