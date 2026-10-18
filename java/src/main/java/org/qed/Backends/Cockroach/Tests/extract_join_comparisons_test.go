package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestExtractJoinComparisons(t *testing.T) {
	rule := loadRule(t, "ExtractJoinComparisons")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	x, a := lc[0], rc[0]
	fx := e.f.Metadata().AddColumn("fx", types.Int)
	ga := e.f.Metadata().AddColumn("ga", types.Int)

	fOfX := func() opt.ScalarExpr { return e.f.ConstructPlus(e.v(x), e.num(1)) }
	gOfA := func() opt.ScalarExpr { return e.f.ConstructPlus(e.v(a), e.num(2)) }
	cond := e.f.ConstructEq(fOfX(), gOfA())

	before := e.f.ConstructInnerJoin(l, r, e.filters(cond), &memo.JoinPrivate{})
	lp := e.f.ConstructProject(l,
		memo.ProjectionsExpr{{Element: fOfX(), Col: fx}}, opt.MakeColSet(x))
	rp := e.f.ConstructProject(r,
		memo.ProjectionsExpr{{Element: gOfA(), Col: ga}}, opt.MakeColSet(a))
	j := e.f.ConstructInnerJoin(lp, rp,
		e.filters(e.f.ConstructEq(e.v(fx), e.v(ga))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(j, memo.EmptyProjectionsExpr, opt.MakeColSet(x, a))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(cond), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r,
		e.filters(cond, e.f.ConstructEq(e.v(x), e.v(a))), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r,
		e.filters(e.f.ConstructEq(
			e.f.ConstructPlus(e.v(x), e.v(a)),
			gOfA())), &memo.JoinPrivate{}))
}
