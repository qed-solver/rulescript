package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestDphypJoinReorder(t *testing.T) {
	rule := loadRule(t, "DphypJoinReorder")
	e := newEnv(t)
	a, ac := e.scan("u", 1)
	b, bc := e.scan("t", 1)
	c, cc := e.scan("nn", 1)
	eq := func(x, y opt.ColumnID) opt.ScalarExpr { return e.f.ConstructEq(e.v(x), e.v(y)) }
	join := func(l, r memo.RelExpr, on memo.FiltersExpr) memo.RelExpr {
		return e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	}
	leftJoin := func(l, r memo.RelExpr, on memo.FiltersExpr) memo.RelExpr {
		return e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
	}
	ab := e.filters(eq(ac[0], bc[0]))

	before := join(join(a, b, ab), c, e.filters(eq(ac[0], cc[0]), eq(bc[0], cc[0])))
	want := join(a, join(b, c, e.filters(eq(bc[0], cc[0]))), e.filters(eq(ac[0], cc[0]), eq(ac[0], bc[0])))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, leftJoin(join(a, b, ab), c, e.filters(eq(ac[0], cc[0]), eq(bc[0], cc[0]))))
	e.expectNoMatch(rule, join(join(a, b, ab), c, e.filters(eq(ac[0], cc[0]), eq(ac[0], cc[0]))))
	e.expectNoMatch(rule, join(a, c, e.filters(eq(ac[0], cc[0]))))
}
