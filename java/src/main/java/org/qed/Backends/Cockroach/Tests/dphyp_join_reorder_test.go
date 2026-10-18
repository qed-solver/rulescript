package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestDphypJoinReorder(t *testing.T) {
	rule := loadRule(t, "DphypJoinReorder")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("nn", 1)
	eq := func(x, y opt.ColumnID) opt.ScalarExpr {
		return e.f.ConstructEq(e.v(x), e.v(y))
	}

	ab := e.f.ConstructInnerJoin(a, b, e.filters(eq(ac[0], bc[0])), &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(ab, c, e.filters(eq(ac[0], cc[0]), eq(bc[0], cc[0])), &memo.JoinPrivate{})
	bc2 := e.f.ConstructInnerJoin(b, c, e.filters(eq(bc[0], cc[0])), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(a, bc2, e.filters(eq(ac[0], cc[0]), eq(ac[0], bc[0])), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(
		e.f.ConstructLeftJoin(a, b, e.filters(eq(ac[0], bc[0])), &memo.JoinPrivate{}),
		c, e.filters(eq(ac[0], cc[0]), eq(bc[0], cc[0])), &memo.JoinPrivate{}))

	e.expectNoMatch(rule, e.f.ConstructSemiJoin(ab, c, e.filters(eq(ac[0], cc[0]), eq(bc[0], cc[0])), &memo.JoinPrivate{}))

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(ab, c, e.filters(eq(ac[0], cc[0])), &memo.JoinPrivate{}))

	a2, a2c := e.scan("t", 2)
	ab2 := e.f.ConstructInnerJoin(a2, b, e.filters(eq(a2c[0], bc[0])), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(ab2, c, e.filters(eq(a2c[0], cc[0]), eq(bc[0], cc[0])), &memo.JoinPrivate{}))

	bc3 := e.f.ConstructInnerJoin(b, c, e.filters(eq(bc[0], cc[0])), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(a, bc3, e.filters(eq(ac[0], cc[0]), eq(ac[0], bc[0])), &memo.JoinPrivate{}))
}
