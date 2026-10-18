package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestAssociateLimitJoinsRight(t *testing.T) {
	rule := loadRule(t, "AssociateLimitJoinsRight")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("nn", 1)
	ab := e.f.ConstructLeftJoin(a, b, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))), &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(c, ab, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))), &memo.JoinPrivate{})
	ac2 := e.f.ConstructInnerJoin(a, c, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))), &memo.JoinPrivate{})
	acb := e.f.ConstructLeftJoin(ac2, b, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(acb, nil, opt.MakeColSet(cc[0], ac[0], bc[0]))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(c, ab, e.filters(e.f.ConstructEq(e.v(bc[0]), e.v(cc[0]))), &memo.JoinPrivate{}))
	inner := e.f.ConstructInnerJoin(a, b, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(c, inner, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))), &memo.JoinPrivate{}))
	c2, c2c := e.scan("nn", 2)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(c2, ab, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(c2c[0]))), &memo.JoinPrivate{}))
}
