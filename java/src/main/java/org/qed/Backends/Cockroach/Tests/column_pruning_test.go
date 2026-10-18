package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestColumnPruning(t *testing.T) {
	rule := loadRule(t, "ColumnPruning")
	e := newEnv(t)
	r, rc := e.scan("t", 3)
	a, b, c := e.v(rc[0]), e.v(rc[1]), e.v(rc[2])
	p0 := e.f.Metadata().AddColumn("p2_c0", types.Int)
	p1 := e.f.Metadata().AddColumn("p2_c1", types.Int)
	p2 := e.f.Metadata().AddColumn("p2_c2", types.Int)
	p3 := e.f.Metadata().AddColumn("p2_c3", types.Int)
	o0 := e.f.Metadata().AddColumn("g0", types.Int)
	o1 := e.f.Metadata().AddColumn("g1", types.Int)

	inner4 := func() memo.RelExpr {
		return e.f.ConstructProject(r, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.f.ConstructPlus(a, b), c), p0),
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(a, c), p1),
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(b, c), p2),
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(a, b), p3),
		}, opt.ColSet{})
	}
	inner3 := func() memo.RelExpr {
		return e.f.ConstructProject(r, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.f.ConstructPlus(a, b), c), p0),
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(a, c), p1),
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(b, c), p2),
		}, opt.ColSet{})
	}
	outer := func(input memo.RelExpr, left, right opt.ColumnID) memo.RelExpr {
		return e.f.ConstructProject(input, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(left), e.num(1)), o0),
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(right), e.num(2)), o1),
		}, opt.ColSet{})
	}

	before := outer(inner4(), p0, p2)
	want := e.f.ConstructProject(
		e.f.ConstructProject(r, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.f.ConstructPlus(a, b), c), p0),
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(b, c), p2),
		}, opt.ColSet{}),
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(p0), e.num(1)), o0),
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(p2), e.num(2)), o1),
		}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, outer(inner4(), p0, p1))
	e.expectNoMatch(rule, outer(inner3(), p0, p2))
	e.expectNoMatch(rule, outer(e.f.ConstructSelect(inner4(), e.filters(e.f.ConstructEq(e.v(p0), e.num(1)))), p0, p2))
}
