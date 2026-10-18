package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRightAssociateJoinsLeft(t *testing.T) {
	rule := loadRule(t, "RightAssociateJoinsLeft")
	t.Run("moves bound predicate to inner join", func(t *testing.T) {
		e := newEnv(t)
		a, ac := e.scan("t", 1)
		b, bc := e.scan("u", 1)
		c, cc := e.scan("nn", 1)
		inner := e.f.ConstructInnerJoin(b, c, memo.TrueFilter, &memo.JoinPrivate{})
		before := e.f.ConstructInnerJoin(a, inner, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0])), e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))), &memo.JoinPrivate{})
		ab := e.f.ConstructInnerJoin(a, b, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))), &memo.JoinPrivate{})
		want := e.f.ConstructInnerJoin(ab, c, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))), &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})
	t.Run("rejects non-inner right join", func(t *testing.T) {
		e := newEnv(t)
		a, ac := e.scan("t", 1)
		b, bc := e.scan("u", 1)
		c, cc := e.scan("nn", 1)
		inner := e.f.ConstructInnerJoin(b, c, nil, &memo.JoinPrivate{})
		before := e.f.ConstructLeftJoin(a, inner, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0])), e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))), &memo.JoinPrivate{})
		e.expectNoMatch(rule, before)
	})
}
