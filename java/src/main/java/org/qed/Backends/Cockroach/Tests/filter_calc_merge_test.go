package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFilterCalcMerge(t *testing.T) {
	rule := loadRule(t, "FilterCalcMerge")
	e := newEnv(t)
	s, c := e.scan("u", 2)
	col0 := e.f.Metadata().AddColumn("e0", types.Int)
	col1 := e.f.Metadata().AddColumn("e1", types.Int)

	e0Expr := func() opt.ScalarExpr { return e.f.ConstructPlus(e.v(c[0]), e.v(c[1])) }
	e1Expr := func() opt.ScalarExpr { return e.f.ConstructMult(e.v(c[0]), e.v(c[1])) }
	bCond := func() opt.ScalarExpr { return e.f.ConstructGt(e.v(c[0]), e.num(0)) }
	tCond := func() opt.ScalarExpr { return e.f.ConstructLt(e.v(col0), e.v(col1)) }
	items := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e0Expr(), col0),
			e.f.ConstructProjectionsItem(e1Expr(), col1),
		}
	}

	bottom := e.f.ConstructSelect(s, e.filters(bCond()))
	mid := e.f.ConstructProject(bottom, items(), opt.ColSet{})
	before := e.f.ConstructSelect(mid, e.filters(tCond()))
	want := e.f.ConstructProject(
		e.f.ConstructSelect(s, e.filters(
			e.f.ConstructLt(e0Expr(), e1Expr()),
			bCond(),
		)),
		items(), opt.ColSet{},
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructProject(bottom, items(), opt.MakeColSet(c[0])),
		e.filters(tCond()),
	))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructProject(s, items(), opt.ColSet{}),
		e.filters(tCond()),
	))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructProject(bottom, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e0Expr(), col0),
		}, opt.ColSet{}),
		e.filters(e.f.ConstructLt(e.v(col0), e.num(5))),
	))
	e.expectNoMatch(rule, bottom)
}
