package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectCalcMerge(t *testing.T) {
	rule := loadRule(t, "ProjectCalcMerge")
	e := newEnv(t)
	s, c := e.scan("u", 2)
	e0 := e.f.Metadata().AddColumn("e0", types.Int)
	e1 := e.f.Metadata().AddColumn("e1", types.Int)
	p0 := e.f.Metadata().AddColumn("p0", types.Int)
	p1 := e.f.Metadata().AddColumn("p1", types.Int)

	e0Expr := func() opt.ScalarExpr { return e.f.ConstructPlus(e.v(c[0]), e.v(c[1])) }
	e1Expr := func() opt.ScalarExpr { return e.f.ConstructMult(e.v(c[0]), e.v(c[1])) }
	bCond := func() opt.ScalarExpr { return e.f.ConstructGt(e.v(c[0]), e.num(0)) }
	innerItems := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e0Expr(), e0),
			e.f.ConstructProjectionsItem(e1Expr(), e1),
		}
	}
	topItems := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(e0), e.v(e1)), p0),
			e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(e0), e.v(e1)), p1),
		}
	}
	mergedItems := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e0Expr(), e1Expr()), p0),
			e.f.ConstructProjectionsItem(e.f.ConstructMult(e0Expr(), e1Expr()), p1),
		}
	}

	bottom := e.f.ConstructSelect(s, e.filters(bCond()))
	mid := e.f.ConstructProject(bottom, innerItems(), opt.ColSet{})
	before := e.f.ConstructProject(mid, topItems(), opt.ColSet{})
	want := e.f.ConstructProject(bottom, mergedItems(), opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructSelect(mid, e.filters(e.f.ConstructLt(e.v(e0), e.v(e1)))),
		topItems(), opt.ColSet{},
	))
	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructProject(s, innerItems(), opt.ColSet{}),
		topItems(), opt.ColSet{},
	))
	e.expectNoMatch(rule, e.f.ConstructProject(mid, topItems(), opt.MakeColSet(e0)))
}
