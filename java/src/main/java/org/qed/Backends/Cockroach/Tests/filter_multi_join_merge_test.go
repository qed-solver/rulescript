package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFilterMultiJoinMerge(t *testing.T) {
	rule := loadRule(t, "FilterMultiJoinMerge")
	e := newEnv(t)
	a, ac := e.scan("t", 3)
	b, bc := e.scan("u", 2)
	c, cc := e.scan("nn", 2)

	ab := e.f.ConstructInnerJoin(a, b, e.filters(), &memo.JoinPrivate{})
	post := e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))
	abc := e.f.ConstructInnerJoin(ab, c, e.filters(post), &memo.JoinPrivate{})
	cond := e.f.ConstructGt(e.v(bc[1]), e.num(0))
	before := e.f.ConstructSelect(abc, e.filters(cond))
	want := e.f.ConstructInnerJoin(ab, c, e.filters(post, cond), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	eqAB := e.f.ConstructEq(e.v(ac[1]), e.v(bc[0]))
	abEq := e.f.ConstructInnerJoin(a, b, e.filters(eqAB), &memo.JoinPrivate{})
	nested := e.f.ConstructInnerJoin(abEq, c, e.filters(post), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(nested, e.filters(cond)))

	leftAbc := e.f.ConstructLeftJoin(ab, c, e.filters(post), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(leftAbc, e.filters(cond)))

	e.expectNoMatch(rule, e.f.ConstructSelect(ab, e.filters(cond)))
}
