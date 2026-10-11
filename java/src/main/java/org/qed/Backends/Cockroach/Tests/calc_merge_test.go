package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestCalcMerge(t *testing.T) {
	rule := loadRule(t, "CalcMerge")
	e := newEnv(t)
	s, c := e.scan("u", 2)
	b1 := e.f.Metadata().AddColumn("b1", types.Int)
	b2 := e.f.Metadata().AddColumn("b2", types.Int)
	t1 := e.f.Metadata().AddColumn("t1", types.Int)
	t2 := e.f.Metadata().AddColumn("t2", types.Int)

	b1Expr := func() opt.ScalarExpr { return e.f.ConstructPlus(e.v(c[0]), e.v(c[1])) }
	b2Expr := func() opt.ScalarExpr { return e.f.ConstructMult(e.v(c[0]), e.v(c[1])) }
	bCond := func() opt.ScalarExpr { return e.f.ConstructGt(e.v(c[0]), e.num(0)) }
	topItems := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(b1), e.v(b2)), t1),
			e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(b1), e.v(b2)), t2),
		}
	}

	bottom := e.f.ConstructSelect(s, e.filters(bCond()))
	mid := e.f.ConstructProject(bottom, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(b1Expr(), b1),
		e.f.ConstructProjectionsItem(b2Expr(), b2),
	}, opt.ColSet{})
	before := e.f.ConstructProject(
		e.f.ConstructSelect(mid, e.filters(e.f.ConstructLt(e.v(b1), e.v(b2)))),
		topItems(), opt.ColSet{},
	)
	want := e.f.ConstructProject(
		e.f.ConstructSelect(s, e.filters(
			e.f.ConstructAnd(bCond(), e.f.ConstructLt(b1Expr(), b2Expr())),
		)),
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(b1Expr(), b2Expr()), t1),
			e.f.ConstructProjectionsItem(e.f.ConstructMult(b1Expr(), b2Expr()), t2),
		}, opt.ColSet{},
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructSelect(mid, e.filters(e.f.ConstructLt(e.v(b1), e.v(b2)))),
		topItems(), opt.MakeColSet(b1),
	))
	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructSelect(bottom, e.filters(e.f.ConstructLt(e.v(b1), e.v(b2)))),
		topItems(), opt.ColSet{},
	))
	e.expectNoMatch(rule, e.f.ConstructProject(mid, topItems(), opt.ColSet{}))
}
