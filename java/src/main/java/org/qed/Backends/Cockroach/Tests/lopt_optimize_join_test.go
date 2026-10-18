package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestLoptOptimizeJoin(t *testing.T) {
	rule := loadRule(t, "LoptOptimizeJoin")
	t.Run("threeWayInnerJoin", func(t *testing.T) {
		e := newEnv(t)
		a, acols := e.scan("t", 1)
		b, bcols := e.scan("u", 1)
		c, ccols := e.scan("nn", 1)
		ab := e.f.ConstructInnerJoin(a, b, memo.TrueFilter, &memo.JoinPrivate{})
		before := e.f.ConstructInnerJoin(ab, c, e.filters(
			e.f.ConstructEq(e.v(acols[0]), e.v(bcols[0])),
			e.f.ConstructEq(e.v(acols[0]), e.v(ccols[0])),
		), &memo.JoinPrivate{})

		ac := e.f.ConstructInnerJoin(a, c, e.filters(
			e.f.ConstructEq(e.v(acols[0]), e.v(ccols[0])),
		), &memo.JoinPrivate{})
		acb := e.f.ConstructInnerJoin(ac, b, e.filters(
			e.f.ConstructEq(e.v(acols[0]), e.v(bcols[0])),
		), &memo.JoinPrivate{})
		want := e.f.ConstructProject(acb, memo.EmptyProjectionsExpr,
			opt.MakeColSet(acols[0], bcols[0], ccols[0]))
		e.expectRewriteUpToFreshColumns(rule, before, want)

		badMiddle := e.f.ConstructInnerJoin(a, b, e.filters(e.f.ConstructEq(e.v(acols[0]), e.num(1))), &memo.JoinPrivate{})
		noMatch := e.f.ConstructInnerJoin(badMiddle, c, e.filters(
			e.f.ConstructEq(e.v(acols[0]), e.v(bcols[0])),
			e.f.ConstructEq(e.v(acols[0]), e.v(ccols[0])),
		), &memo.JoinPrivate{})
		e.expectNoMatch(rule, noMatch)
	})
}
